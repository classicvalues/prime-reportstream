package gov.cdc.prime.router

import gov.cdc.prime.router.Report.Format
import gov.cdc.prime.router.azure.ActionHistory
import gov.cdc.prime.router.azure.BlobAccess
import gov.cdc.prime.router.azure.Event
import gov.cdc.prime.router.azure.ProcessEvent
import gov.cdc.prime.router.azure.WorkflowEngine
import gov.cdc.prime.router.azure.db.enums.TaskAction
import gov.cdc.prime.router.fhirengine.azure.elrProcessQueueName
import gov.cdc.prime.router.fhirengine.engine.RawSubmission
import gov.cdc.prime.router.fhirengine.utils.HL7Reader

/**
 * The base class for a 'receiver' type, currently just for COVID or full ELR submissions. This allows us a fan out
 * point to provide for different pipeline pathways for different submissions types.
 * [workflowEngine] is the workflow engine to use. Mocked engine is passed in for testing
 * [actionHistory] is the actionHistory to use. Mocked ActionHistory is passed in for testing
 */
abstract class SubmissionReceiver(
    internal val workflowEngine: WorkflowEngine = WorkflowEngine(),
    internal val actionHistory: ActionHistory = ActionHistory(TaskAction.receive)
) {
    /**
     * The main function that will be called from ReportFunction. Encapsulates all logic needed to validate
     * and send a submission to the next step in the pipeline, or return error messages to the user.
     *
     * [sender] the sender of the submission
     * [content] string representation of the submission
     * [defaults] defaults to apply
     * [options] Options for processing, if any
     * [routeTo] override for routing
     * [isAsync] true if this will be processed async. ELR senders must have this set to true
     * [allowDuplicates] If true will detect duplicates and return errors if there are any
     * [payloadName] optional sender-determined name of the payload
     * [metadata] metadata to use. Mocked metadata is passed in for testing
     */
    abstract fun validateAndMoveToProcessing(
        sender: Sender,
        content: String,
        defaults: Map<String, String>,
        options: Options,
        routeTo: List<String>,
        isAsync: Boolean,
        allowDuplicates: Boolean,
        payloadName: String?,
        metadata: Metadata? = null
    )

    companion object {
        /**
         * Checks the [report] rows looking for duplicates, and adds errors as needed
         * [workflowEngine] the workflow engine to use. Mocked for testing
         * [report] the report to look at to find out if it contains duplicates
         * [actionLogs] ActionLogger instance to use. Mocked for testing
         */
        internal fun doDuplicateDetection(
            workflowEngine: WorkflowEngine,
            report: Report,
            actionLogs: ActionLogger
        ) {
            // keep list of hashes generated by this ingest to do same-file comparison
            val generatedHashes = mutableListOf<String>()
            val duplicateIndexes = mutableListOf<Int>()
            for (rowNum in 0 until report.itemCount) {
                var itemHash = report.getItemHashForRow(rowNum)
                // check for duplicate item
                val isDuplicate = generatedHashes.contains(itemHash) ||
                    workflowEngine.isDuplicateItem(itemHash)
                if (isDuplicate) {
                    duplicateIndexes.add(rowNum + 1)
                } else {
                    generatedHashes.add(itemHash)
                }
            }
            // TODO Tech Debt: We need a more consistent way to handle 'trackingId'. Once we have one, we can
            //  pass it in here to ensure a trackingId is returned in the response message
            // sanity check - if all items are duplicate
            if (duplicateIndexes.size == report.itemCount) {
                addDuplicateLogs(actionLogs, null, null, null)
            }
            // add logs as needed
            else {
                duplicateIndexes.forEach {
                    addDuplicateLogs(actionLogs, null, it, null)
                }
            }
        }

        /**
         * If a [rowNum] is passed, a DuplicateItemMessage with [payloadName] is added to [actionLogs] as an error.
         * If no [rowNum] is passed, a DuplicateFileMessage with [payloadName] is added to [actionLogs] as an error.
         * If a [trackingId] is passed, it will be used in the response message
         */
        internal fun addDuplicateLogs(
            actionLogs: ActionLogger,
            payloadName: String?,
            rowNum: Int?,
            trackingId: String?
        ) {
            if (rowNum != null) {
                actionLogs.getItemLogger(rowNum, trackingId).error(DuplicateItemMessage())
            } else {
                // duplicate files are always an error, never a warning
                actionLogs.error(DuplicateSubmissionMessage(payloadName))
            }
        }
    }
}

/**
 * Receiver for COVID submissions, contains all logic to parse and move a covid submission to the next step
 * in the pipeline
 */
class CovidReceiver : SubmissionReceiver {
    constructor(
        workflowEngine: WorkflowEngine = WorkflowEngine(),
        actionHistory: ActionHistory = ActionHistory(TaskAction.receive)
    ) : super(workflowEngine, actionHistory)

    override fun validateAndMoveToProcessing(
        sender: Sender,
        content: String,
        defaults: Map<String, String>,
        options: Options,
        routeTo: List<String>,
        isAsync: Boolean,
        allowDuplicates: Boolean,
        payloadName: String?,
        metadata: Metadata?
    ) {
        // parse, check for parse errors

        val (report, actionLogs) = this.workflowEngine.parseCovidReport(sender as CovidSender, content, defaults)

        // prevent duplicates if configured to not allow them
        if (!allowDuplicates) {
            doDuplicateDetection(
                workflowEngine,
                report,
                actionLogs
            )
        }

        workflowEngine.recordReceivedReport(
            report, content.toByteArray(), sender, actionHistory, payloadName
        )

        // if there are any errors, kick this out.
        if (actionLogs.hasErrors()) {
            throw actionLogs.exception
        }

        actionHistory.trackLogs(actionLogs.logs)

        // send to next step
        // call the correct processing function based on processing type
        if (isAsync) {
            processAsync(
                report,
                options,
                defaults,
                routeTo
            )
        } else {
            val routingWarnings = workflowEngine.routeReport(
                report,
                options,
                defaults,
                routeTo,
                actionHistory
            )
            actionHistory.trackLogs(routingWarnings)
        }
    }

    /**
     * Processes a non-ELR report into the async pipeline
     * [parsedReport] The report to process
     * [options] Processing options, if any
     * [defaults] Defaults for missing values, if any
     * [routeTo] Override routeTo value, if any
     */
    internal fun processAsync(
        parsedReport: Report,
        options: Options,
        defaults: Map<String, String>,
        routeTo: List<String>
    ) {

        val report = parsedReport.copy()
        val senderSource = parsedReport.sources.firstOrNull()
            ?: error("Unable to process report ${report.id} because sender sources collection is empty.")
        val senderName = (senderSource as ClientSource).name

        if (report.bodyFormat != Format.INTERNAL) {
            error("Processing a non internal report async.")
        }

        val processEvent = ProcessEvent(Event.EventAction.PROCESS, report.id, options, defaults, routeTo)

        val blobInfo = workflowEngine.blob.generateBodyAndUploadReport(
            report,
            senderName,
            action = processEvent.eventAction
        )
        actionHistory.trackCreatedReport(processEvent, report, blobInfo)

        // add task to task table
        workflowEngine.insertProcessTask(report, report.bodyFormat.toString(), blobInfo.blobUrl, processEvent)
    }
}

/**
 * Receiver for Full ELR, contains logic to process full ELR submissions.
 */
class ELRReceiver : SubmissionReceiver {
    constructor(
        workflowEngine: WorkflowEngine = WorkflowEngine(),
        actionHistory: ActionHistory = ActionHistory(TaskAction.receive)
    ) : super(workflowEngine, actionHistory)

    override fun validateAndMoveToProcessing(
        sender: Sender,
        content: String,
        defaults: Map<String, String>,
        options: Options,
        routeTo: List<String>,
        isAsync: Boolean,
        allowDuplicates: Boolean,
        payloadName: String?,
        metadata: Metadata?
    ) {
        val actionLogs = ActionLogger()

        // check that our input is valid HL7. Additional validation will happen at a later step
        var messages = HL7Reader(actionLogs).getMessages(content)

        // create a Report for this incoming HL7 message to use for tracking in the database
        val sources = listOf(ClientSource(organization = sender.organizationName, client = sender.name))
        val report = Report(
            Format.HL7,
            sources,
            messages.size,
            metadata
        )

        // dupe detection if needed, and if we have not already produced an error
        if (!allowDuplicates && !actionLogs.hasErrors()) {
            doDuplicateDetection(
                workflowEngine,
                report,
                actionLogs
            )
        }

        // record that the submission was received
        val blobInfo = workflowEngine.recordReceivedReport(
            report, content.toByteArray(), sender, actionHistory, payloadName
        )

        // if there are any errors, kick this out.
        if (actionLogs.hasErrors()) {
            throw actionLogs.exception
        }

        // track logs
        actionHistory.trackLogs(actionLogs.logs)

        // add task to task table
        val processEvent = ProcessEvent(Event.EventAction.PROCESS, report.id, options, defaults, routeTo)
        workflowEngine.insertProcessTask(report, report.bodyFormat.toString(), blobInfo.blobUrl, processEvent)

        // move to processing (stick in process-elr queue)
        workflowEngine.queue.sendMessage(
            elrProcessQueueName,
            RawSubmission(
                blobInfo.blobUrl,
                BlobAccess.digestToString(blobInfo.digest),
                sender.fullName
            ).serialize()
        )
    }
}
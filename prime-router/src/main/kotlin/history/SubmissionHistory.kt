package gov.cdc.prime.router.history

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.annotation.JsonValue
import com.microsoft.azure.functions.HttpStatus
import gov.cdc.prime.router.ActionLogLevel
import gov.cdc.prime.router.ClientSource
import gov.cdc.prime.router.ReportStreamFilterResult
import gov.cdc.prime.router.azure.WorkflowEngine
import gov.cdc.prime.router.azure.db.enums.TaskAction
import java.time.OffsetDateTime

/**
 * This class handles ReportFileHistory for Submissions from a sender.
 *
 * @property actionId reference to the `action` table for the action that created this file
 * @property createdAt when the file was created
 * @property sendingOrg the name of the organization that sent this submission
 * @property httpStatus response code for the user fetching this report file
 * @property externalName actual filename of the file
 * @property reportId unique identifier for this specific report file
 * @property schemaTopic the kind of data contained in the report (e.g. "covid-19")
 * @property itemCount number of tests (data rows) contained in the report
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class SubmissionHistory(
    @JsonProperty("submissionId")
    actionId: Long,
    @JsonProperty("timestamp")
    createdAt: OffsetDateTime,
    @JsonProperty("sender")
    val sendingOrg: String,
    httpStatus: Int,
    @JsonInclude(Include.NON_NULL)
    externalName: String? = "",
    @JsonProperty("id")
    reportId: String? = null,
    @JsonProperty("topic")
    schemaTopic: String? = null,
    @JsonProperty("reportItemCount")
    itemCount: Int? = null
) : ReportFileHistory(
    actionId,
    createdAt,
    httpStatus,
    externalName,
    reportId,
    schemaTopic,
    itemCount,
)

/**
 * A `DetailedSubmissionHistory` represents the detailed life history of a submission of a message from a sender.
 *
 * @property actionId of the Submission is `action_id` from the `action` table
 * @property actionName of the Submission is `action_name` from the `action` table
 * @property createdAt of the Submission is `created_at` from the the `action` table
 * @property httpStatus of the Submission is `http_status` from the the `action` table
 * @property reports of the Submission are the Reports related to the action from the `report_file` table
 * @property logs of the Submission are the Logs produced by the submission from the `action_log` table
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder(
    value = [
        "id", "submissionId", "overallStatus", "timestamp", "plannedCompletionAt", "actualCompletionAt",
        "sender", "reportItemCount", "errorCount", "warningCount", "httpStatus", "destinations",
    ]
)
class DetailedSubmissionHistory(
    @JsonProperty("submissionId")
    actionId: Long,
    actionName: TaskAction,
    @JsonProperty("timestamp")
    createdAt: OffsetDateTime,
    httpStatus: Int? = null,
    reports: MutableList<DetailedReport>?,
    logs: List<DetailedActionLog> = emptyList()
) : DetailedReportFileHistory(
    actionId,
    actionName,
    createdAt,
    httpStatus,
    reports,
    logs,
) {
    /**
     * The destinations.
     */
    var destinations = mutableListOf<Destination>()

    /**
     * The sender of the input report.
     */
    var sender: String? = null

    /**
     * The step in the delivery process for a submission
     * Supported values:
     *     ERROR - error on initial submission
     *     RECEIVED - passed the received step in the pipeline and awaits processing/routing
     *     NOT_DELIVERING - processed but has no intended receivers
     *     WAITING_TO_DELIVER - processed but yet to be sent to/downloaded by any receivers
     *     PARTIALLY_DELIVERED - processed, successfully sent to/downloaded by at least one receiver
     *     DELIVERED - processed, successfully sent to/downloaded by all receivers
     *
     * For now, no "send error" type of state.
     *     If a send error occurs for example,
     *     it'll just sit in the waitingToDeliver or
     *     partiallyDelivered state until someone fixes it.
     */
    enum class Status(val printableName: String) {
        ERROR("Error"),
        RECEIVED("Received"),
        NOT_DELIVERING("Not Delivering"),
        WAITING_TO_DELIVER("Waiting to Deliver"),
        PARTIALLY_DELIVERED("Partially Delivered"),
        DELIVERED("Delivered");

        @JsonValue
        override fun toString(): String {
            return printableName
        }
    }

    /**
     * Summary of how far along the submission's process is.
     * The supported values are listed in the Status enum.
     */
    var overallStatus: Status = Status.RECEIVED

    /**
     * When this submission is expected to finish sending.
     * Mirrors the max of all the sendingAt values for this Submission's Destinations
     */
    var plannedCompletionAt: OffsetDateTime? = null

    /**
     * Marks the actual time this submission finished sending.
     * Mirrors the max createdAt of all sent and downloaded reports after it has been sent to all receivers
     */
    var actualCompletionAt: OffsetDateTime? = null

    /**
     * Number of destinations that actually had/will have data sent to.
     */
    val destinationCount: Int get() {
        return destinations.filter { it.itemCount != 0 }.size
    }

    init {
        reports?.forEach { report ->
            // For reports sent to a destination
            report.receivingOrg?.let {
                val filterLogs = logs.filter {
                    it.type == ActionLogLevel.filter && it.reportId == report.reportId
                }
                val filteredReportRows = filterLogs.map { it.detail.message }
                val filteredReportItems = filterLogs.map {
                    ReportStreamFilterResultForResponse(it.detail as ReportStreamFilterResult)
                }
                destinations.add(
                    Destination(
                        report.receivingOrg,
                        report.receivingOrgSvc!!,
                        filteredReportRows,
                        filteredReportItems,
                        report.nextActionAt,
                        report.itemCount,
                        report.itemCountBeforeQualFilter,
                    )
                )
            }

            // For the report received from a sender
            if (report.sendingOrg != null) {
                // There can only be one!
                check(id == null)
                // Reports with errors do not show an ID
                id = if (errorCount == 0) report.reportId.toString() else null
                externalName = report.externalName
                reportItemCount = report.itemCount
                sender = ClientSource(report.sendingOrg, report.sendingOrgClient ?: "").name
                topic = report.schemaTopic
            }
        }
    }

    /**
     * Enrich the submission history with various other related bits of history.
     *
     * @param descendants[] the various bits of DetailedSubmissionHistory that will be used to enrich
     */
    fun enrichWithDescendants(descendants: List<DetailedSubmissionHistory>) {
        check(descendants.distinctBy { it.actionId }.size == descendants.size)
        // Enforce an order on the enrichment:  process, send, download
        descendants.filter { it.actionName == TaskAction.process }.forEach { descendant ->
            enrichWithProcessAction(descendant)
        }
        // note: we do not use any data from the batch action at this time.
        descendants.filter { it.actionName == TaskAction.send }.forEach { descendant ->
            enrichWithSendAction(descendant)
        }
        descendants.filter { it.actionName == TaskAction.download }.forEach { descendant ->
            enrichWithDownloadAction(descendant)
        }
    }

    /**
     * Enrich a parent detailed history with details from the process action.
     * Add destinations, errors, and warnings, to the history details.
     *
     * @param descendant the history used for enriching
     */
    private fun enrichWithProcessAction(descendant: DetailedSubmissionHistory) {
        require(descendant.actionName == TaskAction.process) { "Must be a process action" }

        destinations += descendant.destinations
        errors += descendant.errors
        warnings += descendant.warnings
    }

    /**
     * Enrich a parent detailed history with details from the send action.
     * Add sent report information to each destination present in the parent's historical details.
     *
     * @param descendant the history used for enriching
     */
    private fun enrichWithSendAction(descendant: DetailedSubmissionHistory) {
        require(descendant.actionName == TaskAction.send) { "Must be a send action" }
        descendant.reports?.let { it ->
            it.forEach { report ->
                destinations.find {
                    it.organizationId == report.receivingOrg && it.service == report.receivingOrgSvc
                }?.let {
                    it.sentReports.add(report)
                } ?: run {
                    if (report.receivingOrg != null && report.receivingOrgSvc != null) {
                        destinations.add(
                            Destination(
                                report.receivingOrg,
                                report.receivingOrgSvc,
                                null,
                                null,
                                null,
                                report.itemCount,
                                report.itemCountBeforeQualFilter,
                            )
                        )
                    }
                }
            }
        }
    }

    /**
     * Enrich a parent detailed history with details from the download action
     * Add download report information to each destination present in the parent's historical details.
     *
     * @param descendant the history used for enriching
     */
    private fun enrichWithDownloadAction(descendant: DetailedSubmissionHistory) {
        require(descendant.actionName == TaskAction.download) { "Must be a download action" }

        descendant.reports?.let { it ->
            it.forEach { report ->
                destinations.find {
                    it.organizationId == report.receivingOrg && it.service == report.receivingOrgSvc
                }?.let {
                    it.downloadedReports.add(report)
                } ?: run {
                    if (report.receivingOrg != null && report.receivingOrgSvc != null) {
                        val dest = Destination(
                            report.receivingOrg,
                            report.receivingOrgSvc,
                            null,
                            null,
                            null,
                            report.itemCount,
                            report.itemCountBeforeQualFilter,
                        )

                        destinations.add(dest)
                        dest.downloadedReports.add(report)
                    }
                }
            }
        }
    }

    /**
     *  Update the summary fields for this Submission report based on the destinations that
     *  will be receiving reports.
     */
    fun enrichWithSummary() {
        val realDestinations = destinations.filter { it.itemCount != 0 }

        overallStatus = calculateStatus(realDestinations)
        plannedCompletionAt = calculatePlannedCompletionAt(realDestinations)
        actualCompletionAt = calculateActualCompletionAt(realDestinations)
    }

    /**
     * Runs the calculations for the overallStatus field so that it can be done during init.
     *
     * @param realDestinations[] destinations where items have gone through and thus should be calculated
     * @return The status from the Status enum that matches the current Submission state.
     */
    private fun calculateStatus(realDestinations: List<Destination>): Status {
        if (httpStatus != HttpStatus.OK.value() && httpStatus != HttpStatus.CREATED.value()) {
            return Status.ERROR
        }

        if (destinations.size == 0) {
            /**
             * Cases where this may hit:
             *     1) Data hasn't been processed yet (common in async submissions)
             *     2) Very rare: No data matches any geographical location.
             *         e.g. If both the testing tab and patient data were foreign addresses.
             * At the moment we have NO easy way to distinguish the latter rare case,
             * so it will be treated as status RECEIVED as well.
             */
            return Status.RECEIVED
        } else if (realDestinations.size == 0) {
            return Status.NOT_DELIVERING
        }

        var finishedDestinations = 0

        realDestinations.forEach {
            var sentItemCount = 0

            it.sentReports.forEach {
                sentItemCount += it.itemCount
            }

            var downloadedItemCount = 0

            it.downloadedReports.forEach {
                downloadedItemCount += it.itemCount
            }

            if (sentItemCount >= it.itemCount || downloadedItemCount >= it.itemCount) {
                finishedDestinations++
            }
        }

        if (finishedDestinations >= realDestinations.size) {
            return Status.DELIVERED
        } else if (finishedDestinations > 0) {
            return Status.PARTIALLY_DELIVERED
        }

        return Status.WAITING_TO_DELIVER
    }

    /**
     * Runs the calculations for the plannedCompletionAt field so that it can be done during init.
     *
     * @param realDestinations[] destinations where items have gone through and thus should be calculated
     * @return The timestamp that equals the max of all the sendingAt values for this Submission's Destinations
     */
    private fun calculatePlannedCompletionAt(realDestinations: List<Destination>): OffsetDateTime? {
        if (overallStatus == Status.ERROR ||
            overallStatus == Status.RECEIVED ||
            overallStatus == Status.NOT_DELIVERING
        ) {
            return null
        }

        return realDestinations.maxWithOrNull(compareBy { it.sendingAt })?.sendingAt
    }

    /**
     * Runs the calculations for the actualCompletedAt field so that it can be done during init.
     *
     * @param realDestinations[] destinations where items have gone through and thus should be calculated
     * @return The timestamp that equals the max createdAt of all sent and downloaded reports
     *     after it has been sent to all receivers
     */
    private fun calculateActualCompletionAt(realDestinations: List<Destination>): OffsetDateTime? {
        if (overallStatus != Status.DELIVERED) {
            return null
        }

        val sentReports = realDestinations.filter { it.sentReports.size > 0 }
            .flatMap { it.sentReports }

        val downloadedReports = realDestinations.filter { it.downloadedReports.size > 0 }
            .flatMap { it.downloadedReports }

        return sentReports.plus(downloadedReports).maxWithOrNull(compareBy { it.createdAt })?.createdAt
    }
}

/**
 * Represents the organizations that receive submitted reports from the point of view of a Submission.
 *
 * @property organizationId identifier for the organization that owns this destination
 * @property service the service used by the organization (e.g. elr)
 * @property filteredReportRows filters that were triggered by the contents of the report
 * @property filteredReportItems more structured version of filteredReportRows
 * @property sendingAt the time that this destination is next expecting to receive a report
 * @property itemCount final number of tests available in the report received by the destination
 * @property itemCountBeforeQualFilter total number of tests that were in the submitted report before any filtering
 * @property sentReports logs of reports for this submission sent to this destination
 * @property downloadedReports logs of reports for this submission downloaded for this destination
 */
@JsonPropertyOrder(
    value = [
        "organization", "organizationId", "service", "itemCount", "itemCountBeforeQualFilter", "sendingAt"
    ]
)
@JsonIgnoreProperties(ignoreUnknown = true)
data class Destination(
    @JsonProperty("organization_id")
    val organizationId: String,
    val service: String,
    val filteredReportRows: List<String>?,
    val filteredReportItems: List<ReportStreamFilterResultForResponse>?,
    @JsonProperty("sending_at")
    @JsonInclude(Include.NON_NULL)
    val sendingAt: OffsetDateTime?,
    val itemCount: Int,
    @JsonProperty("itemCountBeforeQualityFiltering")
    val itemCountBeforeQualFilter: Int?,
    var sentReports: MutableList<DetailedReport> = mutableListOf(),
    var downloadedReports: MutableList<DetailedReport> = mutableListOf(),
) {
    /**
     * Finds the name for the organization based on the id provided.
     */
    val organization: String?
        get() = WorkflowEngine.settingsProviderSingleton.findOrganizationAndReceiver(
            "$organizationId.$service"
        )?.let { (org, _) ->
            org.description
        }
}

/**
 * Response use for the API for the filtered report items. This removes unneeded properties that exist in
 * ReportStreamFilterResult. ReportStreamFilterResult is used to serialize and deserialize to/from the database.
 *
 * @param filterResult the filter result to use
 */
data class ReportStreamFilterResultForResponse(@JsonIgnore private val filterResult: ReportStreamFilterResult) {
    /**
     * What kind of filter was triggered.
     */
    val filterType = filterResult.filterType

    /**
     * What was the actual filter triggered.
     */
    val filterName = filterResult.filterName

    /**
     * The name of the test that was filtered out.
     */
    val filteredTrackingElement = filterResult.filteredTrackingElement

    /**
     * What specifically in the filter was triggered.
     */
    val filterArgs = filterResult.filterArgs

    /**
     * Readable version of the various other properties this object has.
     */
    val message = filterResult.message
}
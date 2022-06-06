package gov.cdc.prime.router.history

import assertk.assertThat
import assertk.assertions.isEqualTo
import java.time.OffsetDateTime
import kotlin.test.Test

class DeliveryHistoryTests {
    @Test
    fun `test DeliveryHistory properties init`() {
        DeliveryHistory(
            922,
            OffsetDateTime.parse("2022-04-19T18:04:26.534Z"),
            "ca-dph",
            "elr-secondary",
            201,
            "",
            "b9f63105-bbed-4b41-b1ad-002a90f07e62",
            "covid-19",
            14,
            "http://anyblob.com",
            "test-schema",
            "CSV"
        ).run {
            assertThat(actionId).isEqualTo(922)
            assertThat(createdAt).isEqualTo(OffsetDateTime.parse("2022-04-19T18:04:26.534Z"))
            assertThat(receivingOrg).isEqualTo("ca-dph")
            assertThat(receivingOrgSvc).isEqualTo("elr-secondary")
            assertThat(httpStatus).isEqualTo(201)
            assertThat(externalName).isEqualTo("")
            assertThat(reportId).isEqualTo("b9f63105-bbed-4b41-b1ad-002a90f07e62")
            assertThat(schemaTopic).isEqualTo("covid-19")
            assertThat(itemCount).isEqualTo(14)
            assertThat(bodyUrl).isEqualTo("http://anyblob.com")
            assertThat(schemaName).isEqualTo("test-schema")
            assertThat(bodyFormat).isEqualTo("CSV")

            assertThat(expires).isEqualTo(OffsetDateTime.parse("2022-05-19T18:04:26.534Z"))

            // val compareFilename = Report.formExternalFilename(
            //     bodyUrl,
            //     ReportId.fromString(reportId),
            //     schemaName,
            //     Report.Format.safeValueOf(bodyFormat),
            //     createdAt
            // )

            // assertThat(filename).isEqualTo(compareFilename)
        }
    }
}
package gov.cdc.prime.router.fhirengine.translation.hl7.schema

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEmpty
import assertk.assertions.isTrue
import kotlin.test.Test

class ConfigSchemaTests {
    @Test
    fun `test validate schema`() {
        var schema = ConfigSchema()
        assertThat(schema.isValid()).isFalse()
        assertThat(schema.errors).isNotEmpty()

        schema = ConfigSchema("name")
        assertThat(schema.isValid()).isFalse()
        assertThat(schema.errors).isNotEmpty()

        schema = ConfigSchema("name", "ORU_R01")
        assertThat(schema.isValid()).isFalse()
        assertThat(schema.errors).isNotEmpty()

        schema = ConfigSchema("name", "ORU_R01", "2.5.1")
        assertThat(schema.isValid()).isFalse()
        assertThat(schema.errors).isNotEmpty()

        val goodElement = ConfigSchemaElement("name", value = listOf("Bundle"), hl7Spec = listOf("MSH-7"))
        schema = ConfigSchema("name", "ORU_R01", "2.5.1", listOf(goodElement))
        assertThat(schema.isValid()).isTrue()
        assertThat(schema.errors).isEmpty()

        // A child schema
        schema = ConfigSchema("name", "ORU_R01", "2.5.1", listOf(goodElement))
        assertThat(schema.validate(true)).isNotEmpty()

        // A bad type
        schema = ConfigSchema("name", "VAT", "2.5.1", listOf(goodElement))
        assertThat(schema.isValid()).isFalse()
        assertThat(schema.errors).isNotEmpty()

        schema = ConfigSchema(null, "ORU_R01", "2.5.1", listOf(goodElement))
        assertThat(schema.isValid()).isFalse()
        assertThat(schema.errors).isNotEmpty()

        schema = ConfigSchema("name", null, "2.5.1", listOf(goodElement))
        assertThat(schema.isValid()).isFalse()
        assertThat(schema.errors).isNotEmpty()

        schema = ConfigSchema("name", "ORU_R01", null, listOf(goodElement))
        assertThat(schema.isValid()).isFalse()
        assertThat(schema.isValid()).isFalse() // We check again to make sure we get the same value
        assertThat(schema.errors).isNotEmpty()

        // Check on contants
        schema = ConfigSchema(
            "name", "ORU_R01", "2.5.1", listOf(goodElement), constants = sortedMapOf("const1" to "")
        )
        assertThat(schema.isValid()).isFalse()
        assertThat(schema.errors).isNotEmpty()
        assertThat(schema.errors.size).isEqualTo(1)
        assertThat(schema.errors[0]).contains(schema.constants.firstKey())

        schema = ConfigSchema(
            "name", "ORU_R01", "2.5.1", listOf(goodElement), constants = sortedMapOf("const1" to "value")
        )
        assertThat(schema.isValid()).isTrue()
        assertThat(schema.errors).isEmpty()
    }

    @Test
    fun `test validate schema element`() {
        var element = ConfigSchemaElement()
        assertThat(element.validate()).isNotEmpty()

        element = ConfigSchemaElement("name")
        assertThat(element.validate()).isNotEmpty()

        element = ConfigSchemaElement("name", value = listOf("Bundle"))
        assertThat(element.validate()).isNotEmpty()

        element = ConfigSchemaElement("name", hl7Spec = listOf("MSH-7"))
        assertThat(element.validate()).isNotEmpty()

        element = ConfigSchemaElement("name", value = listOf("Bundle"), hl7Spec = listOf("MSH-7"))
        assertThat(element.validate()).isEmpty()

        element = ConfigSchemaElement("name", value = listOf("Bundle", "Bundle.id"), hl7Spec = listOf("MSH-7"))
        assertThat(element.validate()).isEmpty()

        element = ConfigSchemaElement("name", value = listOf("Bundle"), hl7Spec = listOf("MSH-7"), schema = "schema")
        assertThat(element.validate()).isNotEmpty()

        element = ConfigSchemaElement("name", hl7Spec = listOf("MSH-7"), schema = "schema")
        assertThat(element.validate()).isNotEmpty()

        element = ConfigSchemaElement("name", value = listOf("Bundle"), schema = "schema")
        assertThat(element.validate()).isNotEmpty()

        element = ConfigSchemaElement("name", schema = "schema")
        assertThat(element.validate()).isNotEmpty()

        element = ConfigSchemaElement(
            "name", value = listOf("Bundle"), resource = "Bundle", condition = "Bundle",
            hl7Spec = listOf("MSH-7")
        )
        assertThat(element.validate()).isEmpty()

        // FHIR Path errors
        element = ConfigSchemaElement(
            "name", value = listOf("Bundle..."), resource = "Bundle", condition = "Bundle",
        )
        assertThat(element.validate()).isNotEmpty()

        element = ConfigSchemaElement(
            "name", value = listOf("Bundle"), resource = "Bundle...", condition = "Bundle",
        )
        assertThat(element.validate()).isNotEmpty()

        element = ConfigSchemaElement(
            "name", value = listOf("Bundle"), resource = "Bundle", condition = "Bundle...",
        )
        assertThat(element.validate()).isNotEmpty()

        // Check on resource index
        val aSchema = ConfigSchema(
            "schemaname",
            elements = listOf(ConfigSchemaElement("name", value = listOf("Bundle"), hl7Spec = listOf("MSH-7")))
        )
        element = ConfigSchemaElement(
            "name", schema = "someschema", schemaRef = aSchema, resourceIndex = "someindex"
        )
        assertThat(element.validate()).isNotEmpty()
        element = ConfigSchemaElement(
            "name", schema = "someschema", schemaRef = aSchema, resourceIndex = "someindex",
            resource = "someresource"
        )
        assertThat(element.validate()).isEmpty()
        element = ConfigSchemaElement(
            "name", value = listOf("somevalue"), hl7Spec = listOf("MSH-10"), resourceIndex = "someindex",
            resource = "someresource"
        )
        assertThat(element.validate()).isNotEmpty()

        // Check on constants
        element = ConfigSchemaElement(
            "name", value = listOf("somevalue"), hl7Spec = listOf("MSH-10"), constants = sortedMapOf("const1" to "")
        )
        val errors = element.validate()
        assertThat(errors).isNotEmpty()
        assertThat(errors.size).isEqualTo(1)
        assertThat(errors[0]).contains(element.constants.firstKey())
        element = ConfigSchemaElement(
            "name", value = listOf("somevalue"), hl7Spec = listOf("MSH-10"),
            constants = sortedMapOf("const1" to "value")
        )
        assertThat(element.validate()).isEmpty()
    }

    @Test
    fun `test validate schema with schemas`() {
        var goodElement = ConfigSchemaElement("name", value = listOf("Bundle"), hl7Spec = listOf("MSH-7"))
        var childSchema = ConfigSchema("name", elements = listOf(goodElement))
        var elementWithSchema = ConfigSchemaElement("name", schema = "schemaname", schemaRef = childSchema)
        var topSchema = ConfigSchema("name", "ORU_R01", "2.5.1", listOf(elementWithSchema))
        assertThat(topSchema.isValid()).isTrue()
        assertThat(topSchema.errors).isEmpty()

        goodElement = ConfigSchemaElement("name", value = listOf("Bundle")) // No HL7Spec = error
        childSchema = ConfigSchema("name", elements = listOf(goodElement))
        elementWithSchema = ConfigSchemaElement("name", schema = "schemaname", schemaRef = childSchema)
        topSchema = ConfigSchema("name", "ORU_R01", "2.5.1", listOf(elementWithSchema))
        assertThat(topSchema.isValid()).isFalse()
        assertThat(topSchema.errors).isNotEmpty()

        childSchema = ConfigSchema("name", hl7Version = "2.5.1", elements = listOf(goodElement))
        elementWithSchema = ConfigSchemaElement("name", schema = "schemaname", schemaRef = childSchema)
        topSchema = ConfigSchema("name", "ORU_R01", "2.5.1", listOf(elementWithSchema))
        assertThat(topSchema.isValid()).isFalse()
        assertThat(topSchema.errors).isNotEmpty()

        childSchema = ConfigSchema("name", hl7Type = "ORU_R01", elements = listOf(goodElement))
        elementWithSchema = ConfigSchemaElement("name", schema = "schemaname", schemaRef = childSchema)
        topSchema = ConfigSchema("name", "ORU_R01", "2.5.1", listOf(elementWithSchema))
        assertThat(topSchema.isValid()).isFalse()
        assertThat(topSchema.errors).isNotEmpty()
    }
}
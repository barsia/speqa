package io.github.barsia.speqa.filetype

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType
import io.github.barsia.speqa.model.SpeqaDefaults

class SpeqaFrontmatterSchemaProviderFactory : JsonSchemaProviderFactory {
    override fun getProviders(project: Project): List<JsonSchemaFileProvider> {
        return listOf(SpeqaFrontmatterSchemaFileProvider())
    }
}

private class SpeqaFrontmatterSchemaFileProvider : JsonSchemaFileProvider {
    override fun isAvailable(file: VirtualFile): Boolean {
        val name = file.name
        return name.endsWith(".${SpeqaDefaults.TEST_CASE_EXTENSION}") ||
            name.endsWith(".${SpeqaDefaults.TEST_RUN_EXTENSION}")
    }

    override fun getName(): String = "SpeQA Frontmatter"

    override fun getSchemaFile(): VirtualFile? {
        return JsonSchemaProviderFactory.getResourceFile(
            SpeqaFrontmatterSchemaProviderFactory::class.java,
            "/schemas/speqa-frontmatter.json",
        )
    }

    override fun getSchemaType(): SchemaType = SchemaType.embeddedSchema
}

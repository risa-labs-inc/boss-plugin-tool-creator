package ai.rever.boss.plugin.dynamic.toolcreator

import java.io.File

/**
 * Everything the generator needs to scaffold one new plugin repo.
 *
 * All derived names flow from [toolName]: e.g. "Invoice Extractor" becomes
 * slug `invoice-extractor`, package `invoiceextractor`, class prefix
 * `InvoiceExtractor`, pluginId `ai.rever.boss.plugin.dynamic.invoiceextractor`,
 * repo `boss-plugin-invoice-extractor`.
 */
data class ScaffoldSpec(
    val toolName: String,
    val description: String,
    val permissions: Set<ToolPermission>,
    val agent: CliAgent,
    val parentDir: String,
    val createGitHubRepo: Boolean,
) {
    val slug: String = toolName.trim()
        .replace(Regex("[^A-Za-z0-9]+"), "-")
        .trim('-')
        .lowercase()

    val packageName: String = slug.replace("-", "")

    val classPrefix: String = slug.split('-')
        .joinToString("") { part -> part.replaceFirstChar { it.uppercase() } }

    val pluginId: String = "ai.rever.boss.plugin.dynamic.$packageName"

    val repoName: String = "boss-plugin-$slug"

    val repoDir: File = File(parentDir, slug)

    companion object {
        /** Basic validation; returns an error message or null if the spec is buildable. */
        fun validate(toolName: String, description: String, parentDir: String): String? {
            val slug = toolName.trim().replace(Regex("[^A-Za-z0-9]+"), "-").trim('-')
            return when {
                toolName.isBlank() -> "Tool name is required"
                slug.isEmpty() || !slug.first().isLetter() -> "Tool name must start with a letter"
                description.isBlank() -> "Tool description is required"
                parentDir.isBlank() -> "Location is required"
                !File(parentDir).isDirectory -> "Location does not exist: $parentDir"
                File(parentDir, slug.lowercase()).exists() ->
                    "Directory already exists: ${File(parentDir, slug.lowercase()).absolutePath}"
                else -> null
            }
        }
    }
}

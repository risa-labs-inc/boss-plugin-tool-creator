package ai.rever.boss.plugin.dynamic.toolcreator

import java.awt.Dialog
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Window
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

/**
 * Native directory picker parented to the calling window.
 *
 * The host's DirectoryPickerProvider opens an ownerless AWT FileDialog, which
 * on macOS can appear behind (or never focus over) our creation dialog window.
 * Owning the FileDialog by that window keeps z-order and focus correct.
 */
object DirectoryPicker {

    fun pick(owner: Window?, initialDir: String?, onResult: (String?) -> Unit) {
        SwingUtilities.invokeLater {
            val isMacOS = System.getProperty("os.name").lowercase().contains("mac")
            if (isMacOS) {
                System.setProperty("apple.awt.fileDialogForDirectories", "true")
                val dialog = when (owner) {
                    is Dialog -> FileDialog(owner, "Select Location", FileDialog.LOAD)
                    is Frame -> FileDialog(owner, "Select Location", FileDialog.LOAD)
                    else -> FileDialog(null as Frame?, "Select Location", FileDialog.LOAD)
                }
                initialDir?.takeIf { File(it).isDirectory }?.let { dialog.directory = it }
                dialog.isVisible = true
                System.setProperty("apple.awt.fileDialogForDirectories", "false")

                val directory = dialog.directory
                val file = dialog.file
                onResult(
                    when {
                        directory != null && file != null -> File(directory, file).absolutePath
                        directory != null -> directory
                        else -> null
                    }
                )
            } else {
                val chooser = JFileChooser().apply {
                    fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                    dialogTitle = "Select Location"
                    isAcceptAllFileFilterUsed = false
                    currentDirectory = File(
                        initialDir?.takeIf { File(it).isDirectory }
                            ?: System.getProperty("user.home")
                    )
                }
                val result = chooser.showOpenDialog(owner)
                onResult(
                    if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile?.absolutePath
                    else null
                )
            }
        }
    }
}

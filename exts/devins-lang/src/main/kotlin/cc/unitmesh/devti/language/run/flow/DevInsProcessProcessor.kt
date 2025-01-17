package cc.unitmesh.devti.language.run.flow

import cc.unitmesh.devti.gui.chat.ChatActionType
import cc.unitmesh.devti.gui.sendToChatWindow
import cc.unitmesh.devti.language.compiler.DevInsCompiler
import cc.unitmesh.devti.language.psi.DevInFile
import cc.unitmesh.devti.language.psi.DevInVisitor
import cc.unitmesh.devti.provider.ContextPrompter
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.psiUtil.startOffset

@Service(Service.Level.PROJECT)
class DevInsProcessProcessor(val project: Project) {
    /**
     * This function takes a DevInFile as input and returns a list of PsiElements that are comments.
     * It iterates through the DevInFile and adds any comments it finds to the list.
     *
     * @param devInFile the DevInFile to search for comments
     * @return a list of PsiElements that are comments
     */
    private fun lookupFlagComment(devInFile: DevInFile): List<PsiElement> {
        val comments = mutableListOf<PsiElement>()
        devInFile.accept(object : DevInVisitor() {
            override fun visitComment(comment: PsiComment) {
                comments.add(comment)
            }
        })

        return comments
    }

    /**
     * Process the output of a script based on the exit code and flag comment.
     * If the exit code is not 0, attempts to fix the script with LLM.
     * If the exit code is 0 and there is a flag comment, process it.
     *
     * Flag comment format:
     * - [flow]:flowable.devin, means next step is flowable.devin
     * - [flow](result), means a handle with result
     *
     * @param output The output of the script
     * @param event The process event containing the exit code
     * @param scriptPath The path of the script file
     */
    fun process(output: String, event: ProcessEvent, scriptPath: String) {
        val devInFile: DevInFile? = runReadAction { DevInFile.lookup(project, scriptPath) }
        project.service<DevInsConversationService>().updateIdeOutput(scriptPath, output)

        when {
            event.exitCode == 0 -> {
                val comment = lookupFlagComment(devInFile!!).firstOrNull() ?: return
                if (comment.startOffset == 0) {
                    val text = comment.text
                    if (text.startsWith("[flow]")) {
                        val nextScript = text.substring(6)
                        val newScript = DevInFile.lookup(project, nextScript) ?: return
                        this.runTask(newScript)
                    }
                }
            }
            event.exitCode != 0 -> {
                project.service<DevInsConversationService>().tryFixWithLlm(scriptPath)
            }
        }
    }

    private fun runTask(newScript: DevInFile) {
        val compiledResult = DevInsCompiler(project, newScript).compile()
        val prompt = compiledResult.output

        sendToChatWindow(project, ChatActionType.CHAT) { panel, service ->
            service.handlePromptAndResponse(panel, object : ContextPrompter() {
                override fun displayPrompt(): String = prompt
                override fun requestPrompt(): String = prompt
            }, null, true)
        }
    }

    /**
     * 1. We need to call LLM to get the task list
     * 2. According to the input and output to decide the next step
     */
    fun createTasks(): List<DevInFile> {
        TODO()
    }

    /**
     * Generate DevIns Task file by LLM
     */
    fun createTempTaskFile(): DevInFile {
        // TODO
        return DevInFile.fromString(project, "")
    }
}
package cc.unitmesh.devti.intentions

import cc.unitmesh.devti.AutoDevBundle
import cc.unitmesh.devti.editor.inlay.LLMInlayManager
import cc.unitmesh.devti.intentions.task.CompletionTaskRequest
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actions.EditorActionUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiUtilBase
import kotlin.math.min


class CodeCompletionIntention : AbstractChatIntention() {
    override fun getText(): String = AutoDevBundle.message("intentions.chat.code.complete.name")
    override fun getFamilyName(): String = AutoDevBundle.message("intentions.chat.code.complete.family.name")
    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file == null) return

        val document = editor.document
        val offset = editor.caretModel.offset
        val promptLength = 512
        var promptStart = (offset - promptLength).coerceAtLeast(0)
        val isOutBoundary = !EditorActionUtil.isWordBoundary(editor.document.text, promptStart, false, false)
        while (promptStart < offset && isOutBoundary) {
            promptStart++
        }
        if (promptStart == offset) {
            promptStart = (offset - promptLength).coerceAtLeast(0)
        }

        val prefix = document.getText(TextRange.create(promptStart, offset))

        val suffixLength = 256
        var suffixEnd = min((offset + suffixLength).toDouble(), document.textLength.toDouble()).toInt()
        while (suffixEnd > offset && !EditorActionUtil.isWordBoundary(editor.document.text, suffixEnd, false, false)) {
            suffixEnd--
        }

        val element = PsiUtilBase.getElementAtCaret(editor) ?: file
        val suffix = document.getText(TextRange(offset, suffixEnd))

        val request = CompletionTaskRequest.create(editor, offset, element, prefix) ?: return
//        val task = CodeCompletionTask(request)
//        ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, BackgroundableProcessIndicator(task))

        val llmInlayManager = LLMInlayManager.getInstance()
        llmInlayManager
            .editorModified(editor, offset)
    }

    companion object {
        val logger = Logger.getInstance(CodeCompletionIntention::class.java)
    }
}
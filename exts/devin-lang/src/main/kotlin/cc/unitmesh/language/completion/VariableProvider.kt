package cc.unitmesh.language.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.util.ProcessingContext

class VariableProvider: CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
    ) {
        CustomVariable.all().forEach {
            val withTypeText = LookupElementBuilder.create(it.variable).withTypeText(it.description, true)
            result.addElement(withTypeText)
        }
    }
}
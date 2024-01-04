package cc.unitmesh.idea.context

import cc.unitmesh.devti.context.SimpleClassStructure
import cc.unitmesh.devti.isInProject
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.search.searches.ReferencesSearch

object JavaContextCollection {
    val logger = logger<JavaContextCollection>()
    fun findUsages(nameIdentifierOwner: PsiNameIdentifierOwner): List<PsiReference> {
        val project = nameIdentifierOwner.project
        val searchScope = GlobalSearchScope.allScope(project) as SearchScope

        return when (nameIdentifierOwner) {
            is PsiMethod -> {
                MethodReferencesSearch.search(nameIdentifierOwner, searchScope, true)
            }

            else -> {
                ReferencesSearch.search((nameIdentifierOwner as PsiElement), searchScope, true)
            }
        }.findAll().map { it as PsiReference }
    }

    /**
     * This method takes a PsiClass object as input and builds a tree of the class and its fields, including the fields of the fields, and so on. The resulting tree is represented as a HashMap where the keys are the PsiClass objects and the values are ArrayLists of PsiField objects.
     *
     * @param clazz the PsiClass object for which the tree needs to be built
     * @return a HashMap where the keys are the PsiClass objects, and the values are ArrayLists of PsiField objects
     *
     * For example, if a BlogPost class includes a Comment class, and the Comment class includes a User class, then the resulting tree will be:
     *
     * ```
     * parent: BlogPost Psi
     *    child: id
     *    child: Comment
     *        child: User
     *          child: name
     *```
     */
    fun dataStructure(clazz: PsiClass): SimpleClassStructure {
        return simpleStructure(clazz)
    }

    val psiStructureCache = mutableMapOf<String, SimpleClassStructure?>()

    /**
     * Creates a simple class structure for the given PsiClass and search scope.
     *
     * @param clazz the PsiClass for which the simple class structure needs to be created.
     * @return a SimpleClassStructure object representing the simple class structure of the given PsiClass.
     * The object contains the name of the class, the name of the fields, their types, and whether they are built-in or not.
     * If the field type is a primitive type or a boxed type, it is marked as built-in.
     * If the field type is a custom class, the method recursively creates a SimpleClassStructure object for that class.
     * If the field type cannot be resolved, it is skipped.
     */
    fun simpleStructure(clazz: PsiClass): SimpleClassStructure {
        val qualifiedName = clazz.qualifiedName
        if (psiStructureCache.containsKey(qualifiedName)) {
            return psiStructureCache[qualifiedName]!!
        }

        val fields = clazz.fields
        val children = fields.mapNotNull { field ->
            // if current field same to parent class, skip it
            if (field.type == clazz) return@mapNotNull null

            psiStructureCache[field.type.canonicalText]?.let {
                return@mapNotNull it
            }

            val simpleClassStructure = when {
                // like: int, long, boolean, etc.
                field.type is PsiPrimitiveType -> {
                    SimpleClassStructure(field.name, field.type.presentableText, emptyList(), builtIn = true)
                }

                // like: String, List, etc.
                isPsiBoxedType(field.type) -> {
                    SimpleClassStructure(field.name, field.type.presentableText, emptyList(), builtIn = true)
                }

                field.type is PsiClassType -> {
                    // skip for some frameworks like, org.springframework, etc.
                    if (isPopularFrameworks(qualifiedName) == true) return@mapNotNull null

                    val resolve = (field.type as PsiClassType).resolve() ?: return@mapNotNull null
                    if (isJavaBuiltin(resolve) == true) return@mapNotNull null
                    if (resolve is PsiTypeParameter) return@mapNotNull null

                    if (resolve.qualifiedName == qualifiedName) return@mapNotNull null
                    val classStructure = simpleStructure(resolve)
                    classStructure.builtIn = false
                    classStructure
                }

                else -> {
                    psiStructureCache[field.type.canonicalText] = null
                    logger.warn("Unknown supported type: ${field.type}")
                    return@mapNotNull null
                }
            }

            psiStructureCache[field.type.canonicalText] = simpleClassStructure
            simpleClassStructure
        }

        val simpleClassStructure = SimpleClassStructure(clazz.name ?: "", clazz.name ?: "", children)
        if (qualifiedName != null) {
            psiStructureCache[qualifiedName] = simpleClassStructure
        }

        return simpleClassStructure
    }

    private fun isPopularFrameworks(qualifiedName: @NlsSafe String?): Boolean? {
        return qualifiedName?.startsWith("org.springframework") == true
                || qualifiedName?.startsWith("org.apache") == true
                || qualifiedName?.startsWith("org.hibernate") == true
                || qualifiedName?.startsWith("org.slf4j") == true
                || qualifiedName?.startsWith("org.apache") == true
                || qualifiedName?.startsWith("org.junit") == true
                || qualifiedName?.startsWith("org.mockito") == true
    }

    /**
     * Checks if the given PsiType is a boxed type.
     *
     * A boxed type refers to a type that is represented by a PsiClassReferenceType and its resolve() method returns null.
     * This typically occurs when the type is a generic type parameter or a type that cannot be resolved in the current context.
     *
     * @param type the PsiType to be checked
     * @return true if the given type is a boxed type, false otherwise
     */
    private fun isPsiBoxedType(type: PsiType): Boolean {
        if (type !is PsiClassReferenceType) return false

        val resolve = try {
            type.resolve() ?: return true
        } catch (e: Exception) {
            return false
        }

        return isJavaBuiltin(resolve) == true
    }

    private fun isJavaBuiltin(resolve: PsiClass) = resolve.qualifiedName?.startsWith("java.")
}

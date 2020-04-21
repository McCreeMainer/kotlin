/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.codeInsight.daemon.impl.MarkerType
import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.presentation.AttributesTransformerPresentation
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.MouseButton
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.codeInsight.hints.settings.InlayHintsConfigurable
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.searches.DirectClassInheritorsSearch
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.ui.layout.panel
import com.intellij.util.ArrayUtil
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.SmartList
import java.awt.Point
import java.awt.event.MouseEvent
import java.text.MessageFormat
import java.util.*
import javax.swing.JPanel

@Suppress("UnstableApiUsage")
class KotlinCodeVisionProvider : InlayHintsProvider<KotlinCodeVisionProvider.KotlinCodeVisionSettings> {

    override val key: SettingsKey<KotlinCodeVisionSettings> = SettingsKey("CodeLens")
    override val name: String = "Code Vision"
    override val previewText: String? = null

    override fun isLanguageSupported(language: Language): Boolean {
        return language is KotlinLanguage
    }

    override fun createConfigurable(settings: KotlinCodeVisionSettings): ImmediateConfigurable = object : ImmediateConfigurable {
        override fun createComponent(listener: ChangeListener): JPanel {
            return panel {}
        }

        override val cases: List<ImmediateConfigurable.Case>
            get() = listOf(
                ImmediateConfigurable.Case(
                    KotlinBundle.message("hints.title.codevision.usages"),
                    "usages",
                    settings::showUsages
                ),
                ImmediateConfigurable.Case(
                    KotlinBundle.message("hints.title.codevision.inheritors"),
                    "inheritors",
                    settings::showImplementations
                )
            )

        override val mainCheckboxText: String
            get() = KotlinBundle.message("hints.title.codevision.show.hints.for")
    }

    override fun createSettings(): KotlinCodeVisionSettings {
        return KotlinCodeVisionSettings()
    }

    override fun getCollectorFor(
        file: PsiFile, editor: Editor, settings: KotlinCodeVisionSettings,
        sink: InlayHintsSink
    ): InlayHintsCollector? {


        return KotlinCodeVisionHintsCollector(editor, settings)
    }


    class KotlinCodeVisionHintsCollector(editor: Editor, val settings: KotlinCodeVisionSettings) : FactoryInlayHintsCollector(editor) {

        private val FUS_GROUP_ID = "kotlin.code.vision"
        private val USAGES_CLICKED_EVENT_ID = "usages.clicked"
        private val IMPLEMENTATIONS_CLICKED_EVENT_ID = "implementations.clicked"
        private val SETTING_CLICKED_EVENT_ID = "setting.clicked"


        override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
            if (element !is KtProperty && element !is KtNamedFunction && element !is KtClass && element !is KtConstructor<*>) {
                return true
            }

            val hints: MutableList<InlResult> = SmartList()

            if (settings.showUsages) {
                val usagesNum = ReferencesSearch.search(element).count()
                val format = "{0,choice, 0#no usages|1#1 usage|2#{0,number} usages}"
                val usagesHint = StringUtil.capitalizeWords(MessageFormat.format(format, usagesNum), true)
                hints.add(object : InlResult {
                    override fun onClick(editor: Editor, element: PsiElement, event: MouseEvent?) {
                        FUCounterUsageLogger.getInstance().logEvent(editor.project, FUS_GROUP_ID, USAGES_CLICKED_EVENT_ID)
                        GotoDeclarationAction.startFindUsages(editor, editor.project!!, element)
                    }

                    override fun getRegularText(): String {
                        return usagesHint
                    }
                })
            }

            if (settings.showImplementations) {
                if (element is KtFunction) {
                    //val lightClassMethod = LightClassUtil.getLightClassMethod(element)
                    LightClassUtil.getLightClassMethod(element)?.let { it ->
                        val overridingNum = OverridingMethodsSearch.search(it, true).count()

                        hints.add(object : InlResult {
                            override fun onClick(editor: Editor, element: PsiElement, event: MouseEvent?) {
                                val data = FeatureUsageData().addData("location", "method")
                                FUCounterUsageLogger.getInstance().logEvent(editor.project, FUS_GROUP_ID, IMPLEMENTATIONS_CLICKED_EVENT_ID, data)
                                val navigationHandler = MarkerType.OVERRIDDEN_METHOD.navigationHandler
                                navigationHandler.navigate(event, (element as PsiMethod).nameIdentifier)
                            }

                            override fun getRegularText(): String {
                                val prop = "{0, choice, 1#1 Implementation|2#{0,number} Implementations}"
                                return MessageFormat.format(prop, overridingNum)
                            }
                        })
                    }
                } else if (element is KtClass) {
                    val lightClass = element.toLightClass()
                    lightClass?.let {
                        val inheritorsNum = DirectClassInheritorsSearch.search(it, element.useScope, true).count()
                        hints.add(object : InlResult {
                            override fun onClick(editor: Editor, element: PsiElement, event: MouseEvent?) {
                                val data = FeatureUsageData().addData("location", "class")
                                FUCounterUsageLogger.getInstance().logEvent(editor.project, FUS_GROUP_ID, IMPLEMENTATIONS_CLICKED_EVENT_ID, data)
                                val navigationHandler = MarkerType.SUBCLASSED_CLASS.navigationHandler
                                navigationHandler.navigate(event, (element as PsiClass).nameIdentifier)
                            }

                            override fun getRegularText(): String {
                                val prop = "{0, choice, 1#1 Implementation|2#{0,number} Implementations}"
                                return MessageFormat.format(prop, inheritorsNum)
                            }
                        })
                    }
                }
            }

            if (hints.isNotEmpty()) {

                val offset = element.textRange.startOffset
                val line: Int = editor.document.getLineNumber(offset)
                val lineStart: Int = editor.document.getLineStartOffset(line)
                val indent = offset - lineStart

                val presentations = arrayOfNulls<InlayPresentation>(hints.size * 2 + 1)
                presentations[0] = factory.text(StringUtil.repeat(" ", indent))
                var o = 1
                for (i in hints.indices) {
                    val hint: InlResult = hints[i]
                    if (i != 0) {
                        presentations[o++] = factory.text(" ")
                    }
                    presentations[o++] = createPresentation(factory, element, editor, hint)
                }
                presentations[o] = factory.text("          ") // placeholder for "Settings..."


                val seq = factory.seq(*presentations.requireNoNulls())
                val withAppearingSettings = factory.changeOnHover(seq, {
                    val trimmedSpace: Array<InlayPresentation> = Arrays.copyOf(presentations, presentations.size - 1)
                    val spaceAndSettings =
                        arrayOf(factory.text("  "), settings(factory, element, editor))
                    val withSettings =
                        ArrayUtil.mergeArrays(trimmedSpace, spaceAndSettings)
                    factory.seq(*withSettings)
                }) { e: MouseEvent? -> true }
                sink.addBlockElement(lineStart, true, true, 0, withAppearingSettings)
            }
            return true
        }

        private fun createPresentation(
            factory: PresentationFactory,
            element: PsiElement,
            editor: Editor,
            result: InlResult
        ): InlayPresentation {
            val text = factory.smallText(result.getRegularText())
            return factory.changeOnHover(text, {
                val onClick = factory.onClick(
                    text,
                    MouseButton.Left
                ) { event: MouseEvent?, _: Point? ->
                    result.onClick(editor, element, event)

                }
                referenceColor(onClick)
            }) { true }
        }


        private fun referenceColor(presentation: InlayPresentation): InlayPresentation {
            return AttributesTransformerPresentation(
                presentation
            ) {
                val attributes =
                    EditorColorsManager.getInstance().globalScheme
                        .getAttributes(EditorColors.REFERENCE_HYPERLINK_COLOR).clone()
                attributes.effectType = EffectType.LINE_UNDERSCORE
                attributes
            }
        }

        private fun settings(factory: PresentationFactory, element: PsiElement, editor: Editor): InlayPresentation {
            return createPresentation(factory, element, editor, object : InlResult {

                override fun onClick(editor: Editor, element: PsiElement, event: MouseEvent?) {
                    val project = element.project
                    FUCounterUsageLogger.getInstance()
                        .logEvent(project, FUS_GROUP_ID, SETTING_CLICKED_EVENT_ID)
                    InlayHintsConfigurable.showSettingsDialogForLanguage(project, element.language)

                }

                override fun getRegularText(): String {
                    return "Settings..."
                }
            })
        }
    }

    interface InlResult {
        fun onClick(editor: Editor, element: PsiElement, event: MouseEvent?)

        fun getRegularText(): String
    }

    data class KotlinCodeVisionSettings(var showUsages: Boolean = false, var showImplementations: Boolean = false)
}
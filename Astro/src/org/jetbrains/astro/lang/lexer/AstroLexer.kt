// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.astro.lang.lexer

import com.intellij.lang.javascript.JSFlexAdapter
import com.intellij.lang.javascript.JSTokenTypes
import com.intellij.lang.javascript.JSTokenTypes.STRING_TEMPLATE_PART
import com.intellij.lang.javascript.JavaScriptSupportLoader
import com.intellij.lexer.*
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.xml.XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN
import com.intellij.psi.xml.XmlTokenType.XML_BAD_CHARACTER
import com.intellij.psi.xml.XmlTokenType.XML_CHAR_ENTITY_REF
import com.intellij.psi.xml.XmlTokenType.XML_COMMENT_CHARACTERS
import com.intellij.psi.xml.XmlTokenType.XML_DATA_CHARACTERS
import com.intellij.psi.xml.XmlTokenType.XML_REAL_WHITE_SPACE
import com.intellij.psi.xml.XmlTokenType.XML_TAG_CHARACTERS
import com.intellij.psi.xml.XmlTokenType.XML_WHITE_SPACE
import com.intellij.util.containers.Stack
import it.unimi.dsi.fastutil.ints.IntArrayList
import org.jetbrains.astro.lang.lexer.AstroTokenTypes.Companion.FRONTMATTER_SCRIPT

class AstroLexer(val project: Project?, highlightMode: Boolean, private val lexJsFragment: Boolean)
  : HtmlLexer(AstroMergingLexer(AstroFlexAdapter(highlightMode, false), highlightMode), true, highlightMode) {

  private var frontmatterScriptLexer: Lexer? = null

  override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
    if (initialState and HAS_NON_RESTARTABLE_STATE != 0) {
      thisLogger().error(IllegalStateException("Do not reset Astro Lexer to a non-restartable state"))
    }
    frontmatterScriptLexer = null
    super.start(buffer, startOffset, endOffset,
                if (initialState == 0 && lexJsFragment) _AstroLexer.EXPRESSION_INITIAL else initialState)
  }

  override fun isPossiblyCustomTagName(tagName: CharSequence): Boolean {
    return isPossiblyComponentTag(tagName)
  }

  override fun getState(): Int {
    return super.getState() or (if (frontmatterScriptLexer != null) HAS_NON_RESTARTABLE_STATE else 0)
  }

  override fun isRestartableState(state: Int): Boolean {
    return super.isRestartableState(state)
           && (state and HAS_NON_RESTARTABLE_STATE) == 0
  }

  override fun isHtmlTagState(state: Int): Boolean {
    return state == _AstroLexer.START_TAG_NAME || state == _AstroLexer.END_TAG_NAME
  }

  override fun createTagEmbedmentStartTokenSet(): TokenSet {
    return TokenSet.orSet(super.createTagEmbedmentStartTokenSet(), TAG_TOKENS)
  }

  override fun getStateForRestartDuringEmbedmentScan(): Int {
    return _AstroLexer.HTML_INITIAL
  }

  override fun advance() {
    frontmatterScriptLexer?.let {
      it.advance()
      if (it.tokenType == null) {
        frontmatterScriptLexer = null
      }
    }

    if (frontmatterScriptLexer == null) {
      super.advance()
      if (myDelegate.tokenType === FRONTMATTER_SCRIPT) {
        frontmatterScriptLexer = JSFlexAdapter(JavaScriptSupportLoader.TYPESCRIPT.optionHolder, isHighlighting, false)
          .also {
            it.start(myDelegate.bufferSequence, myDelegate.tokenStart, myDelegate.tokenEnd)
          }
      }
    }
  }

  override fun getTokenType(): IElementType? {
    return frontmatterScriptLexer?.tokenType ?: super.getTokenType()
  }

  override fun getTokenStart(): Int {
    return frontmatterScriptLexer?.tokenStart ?: super.getTokenStart()
  }

  override fun getTokenEnd(): Int {
    return frontmatterScriptLexer?.tokenEnd ?: super.getTokenEnd()
  }

  override fun restartAfterEmbedment(offset: Int, baseLexerState: Int) {
    ((myDelegate as? AstroMergingLexer)?.delegate as? AstroFlexAdapter)
      ?.let {
        val flex = it.flex as _AstroLexer
        val (expressionStack, elementNameStack) = flex.run {
          @Suppress("UNCHECKED_CAST")
          Pair(expressionStack.clone(), elementNameStack.clone() as Stack<String>)
        }
        super.restartAfterEmbedment(offset, baseLexerState)
        flex.apply {
          this.expressionStack = expressionStack.clone()
          this.elementNameStack = Stack(elementNameStack)
        }
      }
    ?: super.restartAfterEmbedment(offset, baseLexerState)
  }

  companion object {
    internal val TAG_TOKENS = TokenSet.create(FRONTMATTER_SCRIPT)

    const val HAS_NON_RESTARTABLE_STATE = 1 shl (BASE_STATE_SHIFT + 3)

    fun isPossiblyComponentTag(tagName: CharSequence): Boolean =
      tagName.isNotEmpty() && tagName[0].isUpperCase()

  }

  open class AstroFlexAdapter(highlightMode: Boolean, jsDocTypesMode: Boolean)
    : FlexAdapter(_AstroLexer(highlightMode, jsDocTypesMode)) {

    private val flex get() = (super.getFlex() as _AstroLexer)

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
      flex.expressionStack.clear()
      flex.elementNameStack.clear()
      super.start(buffer, startOffset, endOffset, initialState and HAS_NON_RESTARTABLE_STATE.inv())
    }

    override fun getState(): Int {
      return super.getState() + if (flex.isRestartable) 0 else HAS_NON_RESTARTABLE_STATE
    }

    override fun getCurrentPosition(): LexerPosition {
      return AstroFlexAdapterPosition(tokenStart, super.getState(), flex.expressionStack.clone(), Stack(flex.elementNameStack))
    }

    override fun restore(position: LexerPosition) {
      flex.expressionStack = (position as AstroFlexAdapterPosition).expressionStack.clone()
      flex.elementNameStack = Stack(position.elementNameStack)
      super.start(bufferSequence, position.offset, bufferEnd, position.state)
    }

    private class AstroFlexAdapterPosition(
      private val offset: Int,
      private val state: Int,
      val expressionStack: IntArrayList,
      val elementNameStack: Stack<String>,
    ) : LexerPosition {
      override fun getOffset(): Int = offset

      override fun getState(): Int = state

    }
  }

  private open class AstroMergingLexer(original: FlexAdapter, private val highlightMode: Boolean) : MergingLexerAdapterBase(original) {

    override fun getMergeFunction(): MergeFunction {
      return MergeFunction { type, originalLexer -> this.merge(type, originalLexer) }
    }

    protected open fun merge(type: IElementType?, originalLexer: Lexer): IElementType? {
      var result = type
      while (tokenStart == originalLexer.tokenStart
             && originalLexer.tokenType != null) {
        result = originalLexer.tokenType
        originalLexer.advance()
      }
      if (TOKENS_TO_MERGE.contains(result)) {
        while (true) {
          val nextTokenType = originalLexer.tokenType
          if (nextTokenType !== result) {
            break
          }
          originalLexer.advance()
        }
      }
      if (highlightMode && result === XML_CHAR_ENTITY_REF) {
        while (originalLexer.tokenType === XML_CHAR_ENTITY_REF) {
          originalLexer.advance()
        }
        if (originalLexer.tokenType === XML_ATTRIBUTE_VALUE_TOKEN) {
          return XML_ATTRIBUTE_VALUE_TOKEN
        }
      }
      return result
    }

    private companion object {
      private val TOKENS_TO_MERGE = TokenSet.create(
        XML_COMMENT_CHARACTERS,
        XML_WHITE_SPACE,
        XML_REAL_WHITE_SPACE,
        XML_ATTRIBUTE_VALUE_TOKEN,
        XML_DATA_CHARACTERS,
        XML_TAG_CHARACTERS,
        STRING_TEMPLATE_PART,
        XML_BAD_CHARACTER,
        JSTokenTypes.XML_STYLE_COMMENT,
      )
    }
  }
}


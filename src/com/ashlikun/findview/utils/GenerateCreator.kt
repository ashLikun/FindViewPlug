package utils

import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.openapi.command.WriteCommandAction.Simple
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiIfStatement
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import constant.Constant
import createFieldByElement
import createFieldText
import createFieldsByInitViewMethod
import createFindViewByIdOnClickMethodAndIf
import entitys.Element
import isExtendsActivityOrActivityCompat
import isExtendsFragmentOrFragmentV4
import isExtendsViewCompat
import isImplementsOnClickListener
import layoutInflaterType2Str
import showPopupBalloon
import views.GenerateDialog
import java.util.*


/**
 * 生成代码
 * @author Jowan
 */
class GenerateCreator<T>(
        private val mDialog: GenerateDialog,
        private val mEditor: Editor,
        private val mFile: PsiFile,
        private val mClass: PsiClass,
        private val mProject: Project,
        private val mElements: ArrayList<Element>,
        private val mFactory: PsiElementFactory,
        private val mSelectedText: String,
        private val mIsLayoutInflater: Boolean,
        private val mFindName: String,
        private val mLayoutInflaterText: String,
        private val mLayoutInflaterType: Int,
        command: String
) : Simple<T>(mClass.project, command) {

    private val mOnClickList = ArrayList<Element>()

    init {
    }

    @Throws(Throwable::class)
    override fun run() {
        try {
            generateFindViewById()
        } catch (e: Exception) {
            e.printStackTrace()
            // 异常打印
            mDialog.cancelDialog()
            mEditor.showPopupBalloon(e.message, 10)
            return
        }

        // 重写class
        val styleManager = JavaCodeStyleManager.getInstance(mProject)
        styleManager.optimizeImports(mFile)
        styleManager.shortenClassReferences(mClass)
        ReformatCodeProcessor(mProject, mClass.containingFile, null, false).runWithoutProgress()
        mEditor.showPopupBalloon(Constant.Action.SELECTED_SUCCESS, 5)
    }

    /**
     * 设置变量的值FindViewById，Activity和Fragment,View,Dialog
     */
    private fun generateFindViewById() {
        generateFindViewByIdFields()
        //activity
        if (mProject isExtendsActivityOrActivityCompat mClass) {
            generateFindViewByIdLayoutCode(mFindName, null, "getApplicationContext()")
            return
        }
        //fragment
        if (mProject isExtendsFragmentOrFragmentV4 mClass) {
            generateFindViewByIdLayoutCode(mFindName, if (mFindName == "findViewById") "rootView" else null, "getActivity()")
            return;
        }
        //view
        if (mProject isExtendsViewCompat mClass) {
            generateFindViewByIdLayoutCode("findViewById", null, "getContext()")
            return;
        }
        //其他如dialog
        generateFindViewByIdLayoutCode("findViewById", null, "getContext()")
    }

    /**
     * 创建变量
     */
    private fun generateFindViewByIdFields() {
        for (element in mElements) {
            // 已存在的变量就不创建
            val fields = mClass.allFields
            var duplicateField = false
            for (field in fields) {
                if (!mIsLayoutInflater) {
                    if (field.name != null && field.name == element.fieldName) {
                        duplicateField = true
                        break
                    }
                } else {
                    val layoutField = element.fieldName + layoutInflaterType2Str(mLayoutInflaterText, mLayoutInflaterType)
                    if (field.name != null && field.name == layoutField) {
                        duplicateField = true
                        break
                    }
                }
            }
            // 已存在跳出
            if (duplicateField) {
                continue
            }
            // 设置变量名，获取text里面的内容
            if (element.isEnable) {
                // 添加到class
                mClass.add(mFactory.createFieldFromText(mProject.createFieldText(element, mFile).createFieldByElement(
                        element, mIsLayoutInflater, mLayoutInflaterText, mLayoutInflaterType), mClass))
            }
        }
    }

    /**
     * 写initView方法

     * @param findName findViewById的方法名
     * @param findPre Fragment的话要view.findViewById
     *
     * @param context context
     */
    private fun generateFindViewByIdLayoutCode(findName: String, findPre: String?, context: String) {
        // 判断是否已有initView方法
        val initViewMethods = mClass.findMethodsByName(Constant.Ext.CREATOR_INITVIEW_NAME, false)
        // 有initView方法
        val initViewMethodBody = initViewMethods[0].body
        // 获取initView方法里面的每条内容
        val statements = initViewMethodBody!!.statements
        //开始findView
        if (initViewMethods.isNotEmpty() && initViewMethodBody != null) {
            if (mIsLayoutInflater) {
                // 添加LayoutInflater.from(this).inflate(R.layout.activity_main, null);
                val layoutInflater = "$mLayoutInflaterText = LayoutInflater.from($context).inflate(R.layout.$mSelectedText, null);"
                // 判断是否存在
                var isExist = false
                for (statement in statements) {
                    if (statement.text.contains("R.layout.$mSelectedText")) {
                        isExist = true
                        break
                    } else {
                        isExist = false
                    }
                }
                // 不存在才添加
                if (!isExist) {
                    initViewMethodBody.add(mFactory.createStatementFromText(layoutInflater, initViewMethods[0]))
                }
            }
            for (element in mElements) {
                if (element.isEnable) {
                    // 判断是否已存在findViewById
                    var isFdExist = false
                    var pre = findPre?.let { it + "." } ?: ""
                    var inflater = ""
                    if (mIsLayoutInflater) {
                        inflater = layoutInflaterType2Str(mLayoutInflaterText, mLayoutInflaterType)
                        pre = mLayoutInflaterText + "."
                    }
                    val findViewById = "${element.fieldName}$inflater =${pre}${findName}(${element.fullID});"
                    for (statement in statements) {
                        if (statement.text.contains(element.fullID)) {
                            isFdExist = true
                            break
                        } else {
                            isFdExist = false
                        }
                    }
                    // 不存在就添加
                    if (!isFdExist) {
                        initViewMethodBody.add(mFactory.createStatementFromText(findViewById, initViewMethods[0]))
                    }

                }
            }
        } else {
            mClass.add(mFactory.createMethodFromText(
                    createFieldsByInitViewMethod(findPre, mIsLayoutInflater, mLayoutInflaterText, context, mSelectedText, mElements, mLayoutInflaterType), mClass))
        }
        //开始click相关的
        getFindViewByIdOnClickList()
        if (mOnClickList.size != 0) {
            var inflater = ""
            if (mIsLayoutInflater) {
                inflater = layoutInflaterType2Str(mLayoutInflaterText, mLayoutInflaterType)
            }
            for (element in mOnClickList) {
                if (element.isClickEnable) {
                    // 判断是否已存在setOnClickListener
                    var isClickExist = false
                    val setOnClickListener = "${element.fieldName}$inflater.setOnClickListener(this);"
                    for (statement in statements) {
                        if (statement.text == setOnClickListener) {
                            isClickExist = true
                            break
                        } else {
                            isClickExist = false
                        }
                    }
                    if (!isClickExist && element.isClickable) {
                        initViewMethodBody.add(mFactory.createStatementFromText(setOnClickListener, initViewMethods[0]))
                    }
                }
            }
            generateFindViewByIdOnClickListenerCode()
        }
    }

    /**
     * 添加实现OnClickListener接口
     */
    private fun generateFindViewByIdOnClickListenerCode() {
        // 获取已实现的接口
        val implementsList = mClass.implementsList
        var isImplOnClick = false
        if (implementsList != null) {
            // 获取列表
            val referenceElements = implementsList.referenceElements
            // 是否实现了OnClickListener接口
            isImplOnClick = isImplementsOnClickListener(referenceElements)
        }
        // 未实现添加OnClickListener接口
        if (!isImplOnClick) {
            val referenceElementByFQClassName = mFactory.createReferenceElementByFQClassName("android.view.View.OnClickListener", mClass.resolveScope)
            // 添加的PsiReferenceList
            implementsList?.add(referenceElementByFQClassName)
        }
        generateFindViewByIdClickCode()
    }

    /**
     * 获取有OnClick属性的Element
     */
    private fun getFindViewByIdOnClickList() {
        mElements.filterTo(mOnClickList) { it.isClickEnable && it.isClickable }
    }

    /**
     * 写onClick方法
     */
    private fun generateFindViewByIdClickCode() {
        // 判断是否已有onClick方法
        val onClickMethods = mClass.findMethodsByName(Constant.FIELD_ONCLICK, false)
        // 已有onClick方法
        if (onClickMethods.isNotEmpty() && onClickMethods[0].body != null) {
            var isEnd = false
            val onClickMethodBody = onClickMethods[0].body
            // 获取if
            for (psiElement in onClickMethodBody!!.children) {
                if (psiElement is PsiIfStatement) {
                    isEnd = true;
                    var condition = psiElement.condition!!.context
                    if (condition != null) {
                        // 获取If的内容
                        for (element in mOnClickList) {
                            val elseIf = "if ( v.getId() == ${element.fullID}) {\n}"
//                            // 不存在就添加
                            if (!condition.text.contains(element.fullID)) {
                                condition.add(mFactory.createStatementFromText(elseIf, condition))
                            }
                        }
                    }
                }
            }
            if (!isEnd) {
                val onClick = StringBuilder()
                var isStart = true;
                mOnClickList.forEach {
                    if (isStart) {
                        onClick.append("if ( v.getId() == ${it.fullID}) {\n")
                        isStart = false;
                    } else {
                        onClick.append("else if ( v.getId() == ${it.fullID}) {\n")
                    }
                    onClick.append("}\n")
                }
                onClickMethodBody.add(mFactory.createStatementFromText(onClick.toString(), onClickMethodBody))
            }
            return
        }
        if (mOnClickList.size != 0) {
            mClass.add(mFactory.createMethodFromText(mOnClickList.createFindViewByIdOnClickMethodAndIf(), mClass))
        }
    }

    /**
     * v.getId() == R.id.toolbar

    thenBranch!!.context = if (v.getId() == R.id.toolbar) { Log.e("aaa", "a"); } else if (v.getId() == R.id.viewParent) { }

    thenBranch!!.text = { Log.e("aaa", "a"); }

    elseBranch!!.text = if (v.getId() == R.id.viewParent) { }

    elseBranch!!.context = if (v.getId() == R.id.toolbar) { Log.e("aaa", "a"); } else if (v.getId() == R.id.viewParent) { }

    condition!!.context = if (v.getId() == R.id.toolbar) { Log.e("aaa", "a"); } else if (v.getId() == R.id.viewParent) { }

    condition!!.context = { if (v.getId() == R.id.toolbar) { Log.e("aaa", "a"); } else if (v.getId() == R.id.viewParent) { }  }
     */
}

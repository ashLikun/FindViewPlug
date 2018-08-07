
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiStatement
import constant.Constant

/**
 * 获取initView方法里面的每条数据

 * @param mClass mClass
 *
 * @return PsiStatement[]
 */
fun PsiClass.getInitViewBodyStatements(): Array<PsiStatement>? {
    // 获取initView方法
    val method = this.findMethodsByName(Constant.Ext.CREATOR_INITVIEW_NAME, false)
    return if (method.isNotEmpty() && method[0].body != null)
        method[0].body?.statements
    else null
}

/**
 * 获取onClick方法里面的每条数据

 * @param mClass mClass
 *
 * @return PsiElement[]
 */
fun PsiClass.getOnClickStatement(): Array<PsiElement>? {
    // 获取onClick方法
    val onClickMethods = this.findMethodsByName(Constant.FIELD_ONCLICK, false)
    return if (onClickMethods.isNotEmpty() && onClickMethods[0].body != null)
        onClickMethods[0].body?.children
    else null
}


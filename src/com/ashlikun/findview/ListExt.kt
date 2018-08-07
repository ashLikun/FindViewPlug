import entitys.Element
import java.util.*

/**
 * FindViewById，创建OnClick方法和switch
 *
 * @param mOnClickList 可onclick的Element的集合
 *
 * @return String
 */
fun ArrayList<Element>.createFindViewByIdOnClickMethodAndIf(): String {
    val onClick = StringBuilder()
    onClick.append("@Override public void onClick(View v) {\n")
    var isStart = true;
    this.filter(Element::isClickable).forEach {
        if (isStart) {
            onClick.append("if ( v.getId() == ${it.fullID}) {\n")
            isStart = false;
        } else {
            onClick.append("else if ( v.getId() == ${it.fullID}) {\n")
        }
        onClick.append("}\n")
    }
    onClick.append("}\n")
    return onClick.toString()
}


package top.trumeet.common.widget

import android.content.Context
import android.content.DialogInterface
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import top.trumeet.common.R

/**
 * Created by Trumeet on 2017/12/30.
 */
open class LinkAlertDialog : AlertDialog {
    protected constructor(context: Context) : super(context)
    protected constructor(context: Context, cancelable: Boolean, cancelListener: DialogInterface.OnCancelListener?) : super(
        context,
        cancelable,
        cancelListener
    )
    protected constructor(context: Context, themeResId: Int) : super(context, themeResId)

    class Builder : AlertDialog.Builder {
        constructor(context: Context) : super(context)
        constructor(context: Context, themeResId: Int) : super(context, themeResId)

        /**
         * Set the message to display.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        override fun setMessage(message: CharSequence?): Builder {
            val textView = TextView(context)
            // Try to get AppCompat internal padding, but providing a fallback
            val paddingId = context.resources.getIdentifier("abc_dialog_padding_material", "dimen", context.packageName)
            val padding = if (paddingId != 0) {
                context.resources.getDimensionPixelSize(paddingId)
            } else {
                // Fallback to 16dp
                (16 * context.resources.displayMetrics.density + 0.5f).toInt()
            }
            textView.setPadding(padding, padding, padding, padding)
            textView.movementMethod = LinkMovementMethod.getInstance()
            textView.text = message
            setView(textView)
            return this
        }
    }
}

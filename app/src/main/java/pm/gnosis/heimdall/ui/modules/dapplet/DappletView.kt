package pm.gnosis.heimdall.ui.modules.dapplet

import android.annotation.TargetApi
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.util.AttributeSet
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.WebView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.LinearLayoutCompat
import org.json.JSONArray
import org.json.JSONObject
import pm.gnosis.heimdall.data.repositories.impls.DappletFrame
import pm.gnosis.heimdall.data.repositories.impls.DappletRequest
import android.view.ViewGroup
import androidx.cardview.widget.CardView


class DappletView : LinearLayout {
    @JvmOverloads
    constructor(
            context: Context,
            attrs: AttributeSet? = null,
            defStyleAttr: Int = 0)
            : super(context, attrs, defStyleAttr)

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(
            context: Context,
            attrs: AttributeSet?,
            defStyleAttr: Int,
            defStyleRes: Int)
            : super(context, attrs, defStyleAttr, defStyleRes)

    init {
        this.orientation = VERTICAL
    }

    fun setDappletRequest(dappletRequest: DappletRequest) {
        for (i in 0..(dappletRequest.frames.count() - 1) step 1) {
            val dapplet = dappletRequest.frames[i].dapplet!!
            val views = dapplet.getJSONArray("views")
            val txMeta = dappletRequest.frames[i].txMeta

            for (j in 0..(views.length() - 1) step 1) {
                val view = views.getJSONObject(j)
                val type = view.getString("@type")

                if (type == "view-html-mustache") {
                    addViewHtmlMustache(view, txMeta)
                    break
                } else if (type == "view-plain-mustache") {
                    addViewPlainMustache(view, txMeta)
                    break
                } else if (j == views.length() - 1) {
                    addViewIncompatible()
                }
            }
        }
    }

    private fun addViewHtmlMustache(view: JSONObject, txMeta: JSONObject?) {
        var tpl = view.getString("template")
        if (txMeta != null) {
            txMeta.keys().forEach {
                tpl = tpl.replace("{{" + it + "}}", txMeta.get(it).toString())
            }
        }
        val unencodedHtml = "<html><body>" + tpl + "</body></html>"
        val encodedHtml = Base64.encodeToString(unencodedHtml.toByteArray(), Base64.NO_PADDING)
        val webView = WebView(context)
        webView.loadData(encodedHtml, "text/html", "base64")
        this.addCard(webView)
    }

    private fun addViewPlainMustache(view: JSONObject, txMeta: JSONObject?) {
        var tpl = view.getString("template")
        if (txMeta != null) {
            txMeta.keys().forEach {
                tpl = tpl.replace("{{" + it + "}}", txMeta.get(it).toString())
            }
        }
        val textView = TextView(context)
        textView.setText(tpl)
        this.addCard(textView)
    }

    private fun addViewIncompatible() {
        val textView = TextView(context)
        textView.setText("Incompatible dapplet view")
        this.addCard(textView)
    }

    private fun addCard(child: View) {
        val card = CardView(context)
        val params = ViewGroup.MarginLayoutParams(MATCH_PARENT, WRAP_CONTENT)
        params.bottomMargin = 10
        card.setLayoutParams(params)
        card.setCardBackgroundColor(Color.WHITE)
        card.setRadius(10f)
        card.setCardElevation(3f)
        card.setContentPadding(15,15,15,15)
        card.addView(child)
        card.setUseCompatPadding(true)

        this.addView(card, -1)
    }
}
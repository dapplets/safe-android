package pm.gnosis.heimdall.ui.tokens.balances

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.squareup.picasso.Picasso
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.layout_tokens_item_balance.view.*
import pm.gnosis.heimdall.R
import pm.gnosis.heimdall.data.repositories.models.ERC20Token.Companion.ETHER_TOKEN
import pm.gnosis.heimdall.data.repositories.models.ERC20TokenWithBalance
import pm.gnosis.heimdall.di.ForView
import pm.gnosis.heimdall.di.ViewContext
import pm.gnosis.heimdall.ui.base.Adapter
import pm.gnosis.heimdall.utils.loadTokenImage
import javax.inject.Inject

@ForView
class TokenBalancesAdapter @Inject constructor(
    @ViewContext private val context: Context,
    private val picasso: Picasso
) : Adapter<ERC20TokenWithBalance, TokenBalancesAdapter.ViewHolder>() {

    val tokenSelectedSubject = PublishSubject.create<ERC20TokenWithBalance>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.layout_tokens_item_balance, parent, false)
        return ViewHolder(picasso, view)
    }

    inner class ViewHolder(private val picasso: Picasso, itemView: View) : Adapter.ViewHolder<ERC20TokenWithBalance>(itemView), View.OnClickListener {
        init {
            itemView.setOnClickListener(this)
        }

        override fun bind(data: ERC20TokenWithBalance, payloads: List<Any>) {
            itemView.layout_tokens_item_balance_symbol.text = data.token.symbol
            itemView.layout_tokens_item_balance_balance.text = data.displayString(false)
            itemView.layout_tokens_item_balance_symbol_image.loadTokenImage(picasso, data.token)
        }

        override fun onClick(v: View?) {
            tokenSelectedSubject.onNext(items[adapterPosition])
        }
    }
}

package info.plateaukao.einkbro.proxy

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import info.plateaukao.einkbro.R
import info.plateaukao.einkbro.view.EBToast

class ProxyDashboardActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EBToast.show(this, R.string.proxy_dashboard_not_ready)
        finish()
    }
}

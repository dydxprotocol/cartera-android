package exchange.dydx.carteraexample

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import exchange.dydx.cartera.Test

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val test = Test()
        test.test()
    }
}
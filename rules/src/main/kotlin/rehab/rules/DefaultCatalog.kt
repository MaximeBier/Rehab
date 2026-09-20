package rehab.rules

import rehab.rules.catalog.InstagramRules
import rehab.rules.catalog.TwitterRules

object DefaultCatalog {
    fun create(): RuleCatalog = RuleCatalog(listOf(InstagramRules.PACKAGE_RULES, TwitterRules.PACKAGE_RULES))
}

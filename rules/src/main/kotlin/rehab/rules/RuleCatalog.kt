package rehab.rules

class RuleCatalog(packages: List<PackageRules>) {
    private val byPackage = packages.associateBy { it.packageName }
    val packageNames: Set<String> get() = byPackage.keys
    fun forPackage(packageName: String): PackageRules? = byPackage[packageName]
}

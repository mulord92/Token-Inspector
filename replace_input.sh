awk '
BEGIN { in_func = 0; balance = 0; replaced = 0 }
/^@Composable/ {
    nxt = ""
    getline nxt
    if (nxt ~ /^fun InputSection\(/ && !replaced) {
        in_func = 1
        print "// --- REPLACE START ---"
        next
    } else {
        print $0
        print nxt
        next
    }
}
{
    if (in_func) {
        if (/^}$/ && balance == 1) {
            in_func = 0
            balance = 0
            replaced = 1
            print "// --- REPLACE END ---"
            next
        }
        if (/{/) balance++
        if (/}/) balance--
        next
    }
    print
}
' app/src/main/java/com/example/MainActivity.kt > app/src/main/java/com/example/MainActivity.tmp

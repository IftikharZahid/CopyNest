path = 'app/src/main/java/com/zahidcodes/copynets/ui/SettingsScreen.kt'
with open(path, 'r') as f:
    lines = f.readlines()

lines[561] = '                    Text("This action will permanently delete all saved clips. To confirm, please type \\"Delete All Record\\" below:")\n'

with open(path, 'w') as f:
    f.writelines(lines)
print('Fixed line 561 via fix.py')

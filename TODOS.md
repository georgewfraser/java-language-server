# Todo

## Bugs 
- Deleting file doesn't clear it from javac
- When you move a class to a different package, compiler still thinks it's in the old package
- External delete causes find-references to crash because it's still in FileStore.javaSources()
- Restart debug test doesn't work
- Show 'not used' warnings for non-@Override package-private methods of private classes, because they can only be accessed from same file
- Package template of new package which is sibling of existing package shows sibling + leaf, not parent + leaf.
- `Thing#close()` shows 0 references for `try (thing)`
- Changing `class Foo {}` to `static class Foo {}` doesn't fix "non-static variables this" in `static void test() { new Foo() }`
- Fields don't show up in debugger
- Debugger doesn't print arrays nicely
- Imports are disappearing when errors are present
- Delete imports from my own package
- Generated method doesn't have static modifier when appropriate
- Add import code action doesn't appear in extends clause
- Docs are not getting resolved for field completions.
- Create missing method always places the method in the current class, even if it belongs somewhere else.
- Select entire name, find references => no results
- Crashes if maven is not installed
- Homebrew users don't have src.zip, detect java version and download the appropriate src.zip

## Optimizations
- Compilation is very slow in the presence of lots of errors
- Use package graph to limit search for find-usages/goto-def
- Package separately for windows, mac, linux (see https://github.com/rust-lang/rust-analyzer/blob/master/.github/workflows/release.yaml)

# Features
- Autocomplete new method name based on "no such method" errors.
- Search for methods in source and deps based on return type, parameters
- "Find implementations" code lens on interfaces
- Rename other types (see JavaLanguageServer#canRename)
- set.contains(wrongType) should show some kind of warning (and probably other collections methods too)
- Only show 'override inherited method' quick fixes when line is blank.
- Go-to-definition should show references for class/method names
- Show JDK sources in goto def
- Show classpath and JDK sources in debugger
- Warn for impossible null checks


## Refactorings
- Quick fixes
    - Remove parameter
    - Create missing method
    - public is redundant in interface methods
    - static, final are redundant in interfaces inners
    - Fix all imports in package
- Selection
    - Extract constant
    - Extract variable
    - Extract method
    - Change package
    - Add parameter
    - Inline
    - Implement single abstract method
    - Generate cases for enum
- Code lens
    - Inherited methods
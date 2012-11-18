# lein-clr

A Leiningen plugin to automate build tasks for ClojureCLR projects.

Leiningen 2 is required to use this plugin. You can use it for both .NET and Mono.

*Important:* This plugin is in _Alpha_. Please report bugs, share ideas, comments etc.


## Installation

Install as a project level plugin in `project.clj`:

```clojure
:plugins [[lein-clr "0.2.0"]]
```

**Note:** `lein-clr` redefines the environment variables `CLOJURE_LOAD_PATH`
and `CLOJURE_COMPILE_PATH` internally ignoring their original values.


## Usage

### Quickstart in 3 steps -- you must have `curl`/`wget` and `unzip` on `PATH`

(Assuming you are on Windows with a recent version of the .NET framework or Mono.
If you do not have `curl`/`wget` and `unzip` on PATH, consider _Quickstart in 5 steps_
below.)

1. Create a new Leiningen project

    ```batch
    C:\work> lein new lein-clr foo
    C:\work> cd foo
    ```

2. The default `project.clj` does not enable auto download of ClojureCLR. Enable that
   by editing `project.clj` under :clr as follows:
   * (Optional) To use Mono edit `#_"mono"` as `"mono"` under `:cmd-templates/:clj-dep`
   * Uncomment `[:wget :clj-zip :clj-url]` and `[:unzip "../clj" :clj-zip]` under `:deps-cmds`.
   * Under `:main-cmd` and `:compile-cmd` replace `:clj-exe` with `:clj-dep`.

3. Run the build tasks

    ```batch
    C:\work\foo> lein clr test
    C:\work\foo> lein clr run -m foo.core
    C:\work=foo> lein clr -v compile foo.core
    ```

--------

### Quickstart in 5 steps

(Assuming you are on Windows with a recent version of the .NET framework
or Mono.)

1. Download a recent ClojureCLR binary package from here:

   https://github.com/clojure/clojure-clr/downloads

2. Uncompress it into a suitable directory and define an environment variable
   pointing to that directory location, e.g:

   `CLJCLR14_40=C:\clojure-clr-1.4.0-Debug-40`

3. Create a new Leiningen project

   ```batch
   C:\work> lein new lein-clr foo
   C:\work> cd foo
   ```

4. Edit the `:clr/:cmd-templates` section (optional for .NET) in `project.clj`
   to remove `#_"mono"` as shown below:

   ```clojure
   :clj-exe [[CLJCLR14_40 %1]]
   ```

   If you have Mono (on `PATH`) instead of .NET, just uncomment `#_"mono"` as follows:

   ```clojure
   :clj-exe ["mono" [CLJCLR14_40 %1]]
   ```

5. Try the build tasks

   ```batch
   C:\work\foo> lein clr test
   C:\work\foo> lein clr run -m foo.core
   C:\work\foo> lein clr -v compile
   ```

### Build tasks

You can carry out a number of build tasks for ClojureCLR projects
using [Microsoft .NET](http://en.wikipedia.org/wiki/.NET_Framework)
or [Mono](http://www.mono-project.com). A synopsis of the tasks:

```bash
lein clr [-v] clean
lein clr [-v] compile
lein clr [-v] help
lein clr [-v] repl
lein clr [-v] run [-m ns-having-main] [arg1 [arg2] ...]
lein clr [-v] test [test-ns1 [test-ns2] ...]
```

### Project configuration

`lein-clr` uses some regular attributes from `project.clj` for build tasks.
Besides, there are some specific attributes you can refer to in the
`sample.project.clj` file in this repo.


## Getting in touch

Clojure discussion group: https://groups.google.com/group/clojure

Leiningen discussion group: https://groups.google.com/group/leiningen

With me: By [Email](mailto:kumar.shantanu@gmail.com)
or on Twitter: [@kumarshantanu](https://twitter.com/kumarshantanu)


## License

Copyright © 2012 Shantanu Kumar

Distributed under the Eclipse Public License, the same as Clojure.

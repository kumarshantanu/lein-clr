(ns leiningen.new.lein-clr
  (:use [leiningen.new.templates :only [renderer name-to-path ->files]]))

(def render (renderer "lein-clr"))

(defn lein-clr
  "A skeleton project to use with lein-clr"
  [name]
  (let [data {:name name
              :sanitized (name-to-path name)}]
    (->files data
             ["src/{{sanitized}}/core.clj"       (render "core.clj"      data)]
             ["test/{{sanitized}}/core_test.clj" (render "core_test.clj" data)]
             ["doc/intro.md"                     (render "intro.md"      data)]
             ["project.clj"                      (render "project.clj"   data)]
             ["README.md"                        (render "README.md"     data)]
             [".gitignore"                       (render "gitignore"     data)])))

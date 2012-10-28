(defproject lein-clr/parent "0.0.0"
  :description "Housekeeping project for lein-clr"
  :url "https://github.com/kumarshantanu/lein-clr"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.0.0"
  :plugins [[lein-sub "0.2.3"]]
  :sub ["lein-template"
        "plugin"]
  :eval-in :leiningen)
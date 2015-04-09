(ns harja.views.about)

(defmacro lue-gitlog []
  `[~@(take 100 (try (read-string (slurp "resources/gitlog.edn"))
                     (catch java.io.IOException _ [])))])

  

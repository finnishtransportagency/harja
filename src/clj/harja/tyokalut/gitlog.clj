(ns harja.tyokalut.gitlog
  "Tekee lyhyen gitlogin resources alle"
  (:require [clojure.java.shell :as sh]
            [clojure.string :as str]
            ))


(defn log->clj [[hash title author date]]
  {:hash hash
   :title title
   :author author
   :date (.parse (java.text.SimpleDateFormat. "yyyy-MM-dd HH:mm:ss") date)})

(defn -main []
  (let [log (:out (sh/sh "git" "log" "--format=format:\"%h]-[%s]-[%an]-[%ai\""))]
    (spit "resources/gitlog.edn"
          (pr-str (mapv #(log->clj (str/split % #"\]-\["))
                        (str/split log #"\n"))))))
    


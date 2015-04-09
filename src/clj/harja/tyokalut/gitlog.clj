(ns harja.tyokalut.gitlog
  "Tekee lyhyen gitlogin resources alle"
  (:require [clojure.java.shell :as sh]
            [clojure.string :as str]
            ))


(defn log->clj [[hash title author date body]]
  {:hash hash
   :title title
   :body body
   :author author
   :date (.parse (java.text.SimpleDateFormat. "yyyy-MM-dd HH:mm:ss") date)})

(defn -main []
  (let [log (:out (sh/sh "git" "log" "--format=format:%h]-[%s]-[%an]-[%ai]-[%b[:NEXT:]"))]
    (spit "resources/gitlog.edn"
          (pr-str (mapv #(log->clj (str/split % #"\]-\["))
                        (str/split log #"\[:NEXT:\]"))))))
    


(ns harja.tuck-apurit)

(defmacro vaadi-async-kutsut [halutut-set & body]
  `(let [kutsutut# (atom #{})]
     (with-redefs
       [~'tuck/send-async! (fn [r# & _#] (swap! kutsutut# conj r#))]
       ~@body
       (~'is (= ~halutut-set @kutsutut#) "Komento ei kutsunut vaadittuja komentoja"))))
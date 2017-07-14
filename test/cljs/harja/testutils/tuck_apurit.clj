(ns harja.testutils.tuck-apurit)

(defmacro vaadi-async-kutsut [halutut-set & body]
  `(let [kutsutut# (atom #{})]
     (with-redefs
       [~'tuck.core/send-async! (fn [r# & _#] (swap! kutsutut# conj r#))]
       ~@body
       (~'cljs.test/is (= ~halutut-set @kutsutut#) "Komento ei kutsunut vaadittuja komentoja"))))
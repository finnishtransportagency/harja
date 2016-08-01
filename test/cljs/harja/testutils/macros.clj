(ns harja.testutils.macros
  "Testiapureiden makrot"
  (:require [cljs.core.async.macros]
            [cljs.test]))

(defmacro komponenttitesti [komp & testibody]
  `(let [comp# (fn []
                 ~komp)]
     (cljs.test/async
      done#
      (harja.testutils/render [comp#])
      (cljs.core.async.macros/go
        ~@(loop [testit []
                 osio nil
                 [form & forms] testibody]
            (if (nil? form)
              (conj testit osio)
              (if (string? form)
                (recur (if (nil? osio)
                         testit
                         (conj testit osio))
                       `(cljs.test/testing ~form)
                       forms)
                (let [form (if (= form '--)
                             `(cljs.core.async/<! (harja.testutils/paivita))
                             form)]
                  (if osio
                    (recur testit
                           (concat osio [form])
                           forms)
                    (recur (conj testit form)
                           nil
                           forms))))))
        (done#)))))

(ns harja.tiedot.urakka.siirtymat-test
  (:require [harja.tiedot.urakka.siirtymat :as sut]
            [cljs.test :as t :refer-macros [deftest async is]]
            [cljs.core.async :refer [<! timeout]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(deftest odota-ok
  (let [atomi (atom 0)]
    (async
     ok

     ;; Odotellaan atomia, max 1 sekunti
     (go
       (let [ok? (<! (sut/odota-arvoa atomi #(> % 2) 1000))]
         (is ok? "Atomiin ei tullut odotettua arvoa 1s kuluessa"))
       (ok))

     ;; Incrementoidaan 50 ms välein
     (go
       (<! (timeout 50))
       (swap! atomi inc)
       (<! (timeout 50))
       (swap! atomi inc)
       (<! (timeout 50))
       (swap! atomi inc)))))

(deftest odota-ei-ok
  (let [atomi (atom 0)]
    (async
     ok

     ;; Odotellaan atomia, max 1 sekunti
     (go
       (let [ok? (<! (sut/odota-arvoa atomi #(> % 2) 200))]
         (is (not ok?) "Atomiin ei pidä tullua odotettua arvoa 0.2s kuluessa"))
       (ok))

     (go (<! (timeout 50))
         (swap! atomi inc)))))

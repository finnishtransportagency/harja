(ns harja.ui.openlayers.edistymispalkki
  (:require [harja.asiakas.tapahtumat :as tapahtumat]))

(def kuvatason-lataus (atom {:ladataan 0 :ladattu 0}))
(def geometriatason-lataus (atom {:ladataan 0 :ladattu 0}))

(defonce julkaise-kuvatason-lataustapahtuma
         (add-watch kuvatason-lataus ::kuvatason-paivitys
                    (fn [_ _ _ tila]
                      (tapahtumat/julkaise! (assoc tila :aihe :edistymispalkki/kuvataso)))))

(defonce julkaise-geometriatason-lataustapahtuma
         (add-watch geometriatason-lataus ::geometriatason-paivitys
                    (fn [_ _ _ tila]
                      (tapahtumat/julkaise! (assoc tila :aihe :edistymispalkki/geometriataso)))))

(defn nollaa-jos-valmis [{:keys [ladataan ladattu] :as lataus}]
  (if (= ladataan ladattu)
    {:ladataan 0 :ladattu 0}
    lataus))

(defn- aloita-lataus!
  ([atomi] (aloita-lataus! atomi 1))
  ([atomi n] (swap! atomi (comp nollaa-jos-valmis #(update % :ladataan + n)))))

(def kuvataso-aloita-lataus! (partial aloita-lataus! kuvatason-lataus))
(def geometriataso-aloita-lataus! (partial aloita-lataus! geometriatason-lataus))

(defn lataus-valmis!
  ([atomi] (lataus-valmis! atomi 1))
  ([atomi n]
                     (js/setTimeout
                       (fn []
                         (swap! atomi (comp nollaa-jos-valmis
                                            #(update % :ladattu + n))))
                       100)))

(def kuvataso-lataus-valmis! (partial lataus-valmis! kuvatason-lataus))
(def geometriataso-lataus-valmis! (partial lataus-valmis! geometriatason-lataus))

(defn pakota-valmistuminen! [atomi] (reset! atomi {:ladataan 0 :ladattu 0}))
(def geometriataso-pakota-valmistuminen! (partial pakota-valmistuminen! geometriatason-lataus))

(defn pakota-aloitus!
  "Funktiolla voidaan feikata edistymispalkki johonkin tilaan."
  ([atomi] (pakota-aloitus! atomi 1 3))
  ([atomi ladattu ladataan]
   (when (or (not (:ladataan @atomi)) (= 0 (:ladataan @atomi)))
     (swap! atomi #(assoc % :ladattu ladattu))
     (swap! atomi #(assoc % :ladataan ladataan)))))

(def geometriataso-pakota-aloitus! (partial pakota-aloitus! geometriatason-lataus))
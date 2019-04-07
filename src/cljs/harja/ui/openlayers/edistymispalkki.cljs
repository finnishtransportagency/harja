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

(defn valmis? [{:keys [ladataan ladattu]}]
  (>= ladattu ladataan))

(defn nollaa-jos-valmis [lataus]
  (if (valmis? lataus)
    {:ladataan 0 :ladattu 0}
    lataus))

(defn kasvata-ladataan-lukua [{:keys [ladataan ladattu] :as lataus}]
  "Selaimessa piirrettäessä :ladattu saattaa olla :ladataan edellä, koska
  meillä ei ole täydellisiä työkaluja, joilla seurata edistymistä. Kasvatateaan :ladataan
  arvoa, jotta näyttää siltä, että osaamme arvata sinne päin."
  (if (< ladataan ladattu)
    {:ladataan (+ ladataan (* 2 (- ladattu ladataan))) :ladattu ladattu}
    lataus))

;; Näytetään latauspalkki vielä hetken, vaikka homma olisikin oikeasti hoidettu
;; 1000ms kuulosti pitkältä, mutta testauksessa tuntui aika luonnolliselta.
(defn nollaa-ms-jalkeen!
  ([atomi] (nollaa-ms-jalkeen! atomi 1000))
  ([atomi ms]
    ;; Ei tehdä turhaan timeouttia, jos ei lataus ei ole tässä vaiheessa valmis
   (when (valmis? @atomi)
     (js/setTimeout
       (fn []
         ;; Täällä voi lataus olla epävalmis, joten tarkastetaan vielä
         (swap! atomi nollaa-jos-valmis))
       ms))))

(defn- aloita-lataus!
  ([atomi] (aloita-lataus! atomi 1))
  ([atomi n]
   (swap! atomi #(update % :ladataan + n))
   (nollaa-ms-jalkeen! atomi)))

(defn kuvataso-aloita-lataus! [] (aloita-lataus! kuvatason-lataus))
(def geometriataso-aloita-lataus! (partial aloita-lataus! geometriatason-lataus))

(defn lataus-valmis!
  ([atomi] (lataus-valmis! atomi 1))
  ([atomi n]
   (js/setTimeout
     (fn []
       (swap! atomi (comp kasvata-ladataan-lukua #(update % :ladattu + n)))
       (nollaa-ms-jalkeen! atomi))
     100)))

(defn kuvataso-lataus-valmis! [] (lataus-valmis! kuvatason-lataus))
(def geometriataso-lataus-valmis! (partial lataus-valmis! geometriatason-lataus))

(defn pakota-valmistuminen! [atomi]
  (js/setTimeout
    (fn []
      (swap! atomi #(assoc % :ladattu (:ladataan %)))
      (nollaa-ms-jalkeen! atomi))
    100))
(def geometriataso-pakota-valmistuminen! (partial pakota-valmistuminen! geometriatason-lataus))

(defn pakota-aloitus!
  "Funktiolla voidaan feikata edistymispalkki johonkin tilaan."
  ([atomi] (pakota-aloitus! atomi 1 100))
  ([atomi ladattu ladataan]
   (when (= 0 (:ladataan @atomi))
     (reset! atomi {:ladataan ladataan :ladattu ladattu}))))

(def geometriataso-pakota-aloitus! (partial pakota-aloitus! geometriatason-lataus))

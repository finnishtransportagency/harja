(ns harja.tiedot.tieluvat.tieluvat
  (:require [reagent.core :as r :refer [atom]]
            [tuck.core :as tuck]
            [cljs.core.async :refer [<!]]
            [harja.loki :refer [log]]
            [harja.tyokalut.tuck :as tt]
            [harja.ui.viesti :as viesti]
            [harja.ui.protokollat :as protokollat]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tyokalut.spec-apurit :as spec-apurit]

            [harja.domain.tielupa :as tielupa]
            [harja.pvm :as pvm]
            [clojure.set :as set])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def tila (atom {:valinnat nil
                 :valittu-tielupa nil
                 :tielupien-haku-kaynnissa? false
                 :nakymassa? false
                 :kayttajan-urakat [nil]}))

(def valintojen-avaimet [:tr :luvan-numero :lupatyyppi :hakija :voimassaolo :sijainti
                         :myonnetty])

(defrecord MuutaTila [polku arvo])
(defrecord Nakymassa? [nakymassa?])
(defrecord PaivitaValinnat [])
(defrecord HaeTieluvat [valinnat aikaleima])
(defrecord TieluvatHaettu [tulos aikaleima])
(defrecord TieluvatEiHaettu [virhe aikaleima])
(defrecord ValitseTielupa [tielupa])
(defrecord AvaaTielupaPaneelista [id])
(defrecord KayttajanUrakat [])
(defrecord KayttajanUrakatHakuOnnistui [vastaus])
(defrecord KayttajanUrakatHakuEpaonnistui [vastaus])

(defn hakuparametrit [valinnat]
  (or
    (spec-apurit/poista-nil-avaimet
      (assoc {} ::tielupa/hakija-nimi (get-in valinnat [:hakija ::tielupa/hakija-nimi])
                ::tielupa/tyyppi (:lupatyyppi valinnat)
                ::tielupa/paatoksen-diaarinumero (:luvan-numero valinnat)
                ::tielupa/voimassaolon-alkupvm (first (:voimassaolo valinnat))
                ::tielupa/voimassaolon-loppupvm (second (:voimassaolo valinnat))
                :myonnetty (:myonnetty valinnat)

                ::tielupa/haettava-tr-osoite
                (let [tie (get-in valinnat [:tr :numero])
                      aosa (get-in valinnat [:tr :alkuosa])
                      aet (get-in valinnat [:tr :alkuetaisyys])
                      losa (get-in valinnat [:tr :loppuosa])
                      let (get-in valinnat [:tr :loppuetaisyys])]
                  {::tielupa/tie tie
                   ::tielupa/aosa aosa
                   ::tielupa/aet aet
                   ::tielupa/losa (when (and losa let) losa)
                   ::tielupa/let (when (and losa let) let)

                   #_#_::tielupa/geometria (:sijainti valinnat)})))
    {}))

(def hakijahaku
  (reify protokollat/Haku
    (hae [_ teksti]
      (go (let [vastaus (<! (k/post! :hae-tielupien-hakijat {:hakuteksti teksti}))]
            vastaus)))))

(defn nayta-kentat?
  "Ottaa lomakkeen kenttäskeeman ja näytettävän datan.
  Palauttaa true, jos edes jollekin skeeman kentälle data sisältää arvon, joka ei ole
  nil tai tyhjä lista."
  [kentat tielupa]
  (let [kentat (->> tielupa
                    kentat
                    :skeemat
                    (map #(or (:hae %) (:nimi %))))]
    (boolean
      (when (and (some? kentat) (not-empty kentat))
        ((apply
           some-fn
           (map
             (partial
               comp
               (fn [kentan-arvo]
                 (if (coll? kentan-arvo)
                   (not-empty kentan-arvo)
                   (some? kentan-arvo))))
             kentat))
          tielupa)))))

(defn pelkat-vapaat-sijainnit
  "Tieluvalla on mainos-, johtoasennus- yms. tietoja, joilla on sijaintietoja.
  Lisäksi tieluvalla on yleinen 'sijainnit'-taulukko. Nämä saattavat sisältää redundanttia tietoja,
  datan eheydestä riippuen.

  Funktio palauttaa vain ne 'sijainnit' listan arvot, jotka eivät löydy 'tarkemmista' listoista."
  [valittu-tielupa]
  (apply
    set/difference
    (map
      (fn [osio]
        (set
          (map
            (fn [sijainti]
              (select-keys
                sijainti
                [::tielupa/tie
                 ::tielupa/aosa
                 ::tielupa/aet
                 ::tielupa/losa
                 ::tielupa/let
                 ::tielupa/ajorata
                 ::tielupa/kaista
                 ::tielupa/puoli]))
            osio)))
      ((juxt ::tielupa/sijainnit
             ::tielupa/mainokset
             ::tielupa/liikennemerkkijarjestelyt
             ::tielupa/johtoasennukset
             ::tielupa/kaapeliasennukset)
        valittu-tielupa))))

(extend-protocol tuck/Event
  MuutaTila
  (process-event [{:keys [polku arvo]} app]
      (assoc-in app polku arvo))

  Nakymassa?
  (process-event [{n :nakymassa?} app]
    (assoc app :nakymassa? n))

  PaivitaValinnat
  (process-event [_ app]
    (let [aikaleima (pvm/nyt)
          valinnat (:valinnat app)]
      ((tuck/send-async! ->HaeTieluvat) valinnat aikaleima)
      (assoc app :tielupien-haku-kaynnissa? true
                 :nykyinen-haku aikaleima)))

  HaeTieluvat
  (process-event [{valinnat :valinnat aikaleima :aikaleima} app]
    (let [parametrit (hakuparametrit valinnat)
          aikaleima (or aikaleima (pvm/nyt))]
      (-> app
          (tt/post! :hae-tieluvat
                    parametrit
                    {:onnistui ->TieluvatHaettu
                     :onnistui-parametrit [aikaleima]
                     :epaonnistui ->TieluvatEiHaettu
                     :epaonnistui-parametrit [aikaleima]})
          (assoc :tielupien-haku-kaynnissa? true
                 ;; Aikakenttäkomponentti päivittää tilaansa bugisesti kahdesti, kun sinne syöttää arvon
                 ;; Kun antaa aikavälin alun, päivittyy tilaan aluksi [nil nil], joka laukaisee haun.
                 ;; Täten kun käyttäjä antaa aikavälin toisen osan, on haku jo käynnissä. Tämän takia uuden
                 ;; haun tekemistä, kun vanha on käynnissä, ei voi estää. Sen sijaan otetaan taulukkoon
                 ;; aina vain uusimman haun tulos.
                 :nykyinen-haku aikaleima))))

  TieluvatHaettu
  (process-event [{t :tulos a :aikaleima} app]
    (if (= a (:nykyinen-haku app))
      (assoc app :tielupien-haku-kaynnissa? false
                 :haetut-tieluvat t
                 :nykyinen-haku nil)

      app))

  TieluvatEiHaettu
  (process-event [{v :virhe a :aikaleima} app]
    (if (= a (:nykyinen-haku app))
      (do (viesti/nayta! "Tielupien haku epäonnistui!" :danger)
          (assoc app :tielupien-haku-kaynnissa? false
                     :nykyinen-haku nil))

      app))

  ValitseTielupa
  (process-event [{t :tielupa} app]
    (assoc app :valittu-tielupa t))

  AvaaTielupaPaneelista
  (process-event [{id :id} app]
    (assoc app :valittu-tielupa (first (filter #(= id (::tielupa/id %)) (:haetut-tieluvat app)))))
  KayttajanUrakat
  (process-event [_ app]
    (-> app
        (tt/post! :kayttajan-urakat
                  []
                  {:onnistui ->KayttajanUrakatHakuOnnistui
                   :epaonnistui ->KayttajanUrakatHakuEpaonnistui})
        (assoc :kayttajan-urakoiden-haku-kaynnissa? true)))
  KayttajanUrakatHakuOnnistui
  (process-event [{kayttajan-urakat :vastaus} app]
    (assoc app :kayttajan-urakoiden-haku-kaynnissa? false
           :kayttajan-urakat (into [nil] (mapcat :urakat kayttajan-urakat))))
  KayttajanUrakatHakuEpaonnistui
  (process-event [_ app]
    (viesti/nayta! "Urakoiden haku epäonnistui!" :danger)
    (assoc app :kayttajan-urakoiden-haku-kaynnissa? false)))

(ns harja.tiedot.tieluvat.tielupa-tiedot
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
(defrecord HaeAlueurakat [])
(defrecord HaeAlueurakatOnnistui [vastaus])
(defrecord HaeAlueurakatEpaonnistui [vastaus])
(defrecord HaeTielupaOnnistui [vastaus])
(defrecord HaeTielupaEpaonnistui [vastaus])

(defn hakuparametrit [valinnat]
  (let [tie (get-in valinnat [:tr :numero])
        aosa (get-in valinnat [:tr :alkuosa])
        aet (get-in valinnat [:tr :alkuetaisyys])
        losa (get-in valinnat [:tr :loppuosa])
        let (get-in valinnat [:tr :loppuetaisyys])]
    (or (spec-apurit/poista-nil-avaimet
          (assoc {} ::tielupa/hakija-nimi (get-in valinnat [:hakija ::tielupa/hakija-nimi])
                    ::tielupa/tyyppi (:lupatyyppi valinnat)
                    ::tielupa/paatoksen-diaarinumero (:luvan-numero valinnat)
                    ::tielupa/voimassaolon-alkupvm (first (:voimassaolo valinnat))
                    ::tielupa/voimassaolon-loppupvm (second (:voimassaolo valinnat))
                    :myonnetty (:myonnetty valinnat)
                    :urakka-id (get-in valinnat [:urakka :id])
                    :alueurakkanro (get-in valinnat [:alueurakka :harja.domain.alueurakka-domain/alueurakkanro])

                    ::tielupa/haettava-tr-osoite
                    {::tielupa/tie tie
                     ::tielupa/aosa aosa
                     ::tielupa/aet aet
                     ::tielupa/losa (when (and losa let) losa)
                     ::tielupa/let (when (and losa let) let)

                     #_#_::tielupa/geometria (:sijainti valinnat)}))
      {})))

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

(defn lisaa-puuttuva-aika
  [[alku loppu]]
  {:pre [(or alku loppu)
         (not (and alku loppu))]
   :post [(fn [[alku loppu]]
            (and alku loppu))]}
  (let [alku (or alku (pvm/paivan-alussa-opt (pvm/ajan-muokkaus loppu false 1 :kuukausi)))
        loppu (or loppu (pvm/paivan-alussa-opt (pvm/myohaisin (pvm/nyt) alku)))]
    [alku loppu]))

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

(defn- hae-lupa [id]
  (tt/post! :hae-tielupa
    {:id id}
    {:onnistui ->HaeTielupaOnnistui
     :epaonnistui ->HaeTielupaEpaonnistui}))

(extend-protocol tuck/Event
  MuutaTila
  (process-event [{:keys [polku arvo]} app]
    (assoc-in app polku arvo))

  Nakymassa?
  (process-event [{n :nakymassa?} app]
    (-> app
      (assoc :nakymassa? n)
      (assoc :alueurakat nil)))

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

  HaeTielupaOnnistui
  (process-event [{vastaus :vastaus} app]

    (-> app
      (assoc :valittu-tielupa vastaus)
      (assoc :tielupien-haku-kaynnissa? false)))

  HaeTielupaEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (viesti/nayta! "Tielupien haku epäonnistui!" :danger)
      (assoc app :tielupien-haku-kaynnissa? false)
      app))

  ValitseTielupa
  (process-event [{t :tielupa} app]
    (do
      (when (::tielupa/id t)
        (hae-lupa (::tielupa/id t)))
      (-> app
        (assoc :valittu-tielupa nil))))

  HaeAlueurakat
  (process-event [_ app]
    (-> app
      (tt/post! :hae-alueurakat
        []
        {:onnistui ->HaeAlueurakatOnnistui
         :epaonnistui ->HaeAlueurakatEpaonnistui})
      (assoc :alueurakka-haku-kaynnissa? true)))

  HaeAlueurakatOnnistui
  (process-event [{vastaus :vastaus} app]
    (-> app
      (assoc :alueurakka-haku-kaynnissa? false)
      (assoc :alueurakat (conj vastaus {:harja.domain.alueurakka-domain/nimi "- Ei käytössä -" :harja.domain.alueurakka-domain/alueurakkanro nil}))))

  HaeAlueurakatEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta! "Urakoiden haku epäonnistui!" :danger)
    (assoc app :alueurakka-haku-kaynnissa? false
               :alueurakat nil))

  AvaaTielupaPaneelista
  (process-event [{id :id} app]
    (do
      (hae-lupa id)
      (assoc app :valittu-tielupa nil)))

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
    (try (assoc app
           :kayttajan-urakoiden-haku-kaynnissa? false
           :kayttajan-urakat (sort-by :nimi
                               (transduce (comp
                                            (filter #(= (:tyyppi %) :hoito))
                                            (mapcat :urakat))
                                 conj
                                 [nil]
                                 kayttajan-urakat)))
         (catch :default _
           (viesti/nayta! "Urakoiden hakuvastauksen käsittely epäonnistui!" :danger)
           (assoc app :kayttajan-urakoiden-haku-kaynnissa? false))))

  KayttajanUrakatHakuEpaonnistui
  (process-event [_ app]
    (viesti/nayta! "Urakoiden haku epäonnistui!" :danger)
    (assoc app :kayttajan-urakoiden-haku-kaynnissa? false)))

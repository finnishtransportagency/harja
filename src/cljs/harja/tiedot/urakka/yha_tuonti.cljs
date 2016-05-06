(ns harja.tiedot.urakka.yhatuonti
  (:require [reagent.core :refer [atom] :as reagent]
            [harja.ui.yleiset :refer [ajax-loader vihje]]
            [harja.ui.viesti :as viesti]
            [harja.loki :refer [log logt tarkkaile!]]
            [cljs.core.async :refer [<! >! chan timeout]]
            [harja.ui.lomake :refer [lomake]]
            [harja.ui.grid :refer [grid]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tyokalut.vkm :as vkm]
            [harja.tiedot.navigaatio :as nav]
            [cljs-time.core :as t]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.istunto :as istunto]
            [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]))

(def hakulomake-data (atom nil))
(def hakutulokset-data (atom []))
(def sidonta-kaynnissa? (atom false))

(defn hae-yha-urakat [{:keys [yhatunniste sampotunniste vuosi harja-urakka-id]}]
  (log "[YHA] Hae YHA-urakat...")
  (reset! hakutulokset-data nil)
  (k/post! :hae-urakat-yhasta {:harja-urakka-id harja-urakka-id
                               :yhatunniste yhatunniste
                               :sampotunniste sampotunniste
                               :vuosi vuosi}))


(defn sido-yha-urakka-harja-urakkaan [harja-urakka-id yha-tiedot]
  (k/post! :sido-yha-urakka-harja-urakkaan {:harja-urakka-id harja-urakka-id
                                            :yha-tiedot yha-tiedot}))

(defn- tallenna-uudet-yha-kohteet [harja-urakka-id kohteet]
  (k/post! :tallenna-uudet-yha-kohteet {:urakka-id harja-urakka-id
                                        :kohteet kohteet}))

(defn- hae-yha-kohteet [harja-urakka-id]
  (log "[YHA] Haetaan YHA-kohteet urakalle id:llä" harja-urakka-id)
  (k/post! :hae-yha-kohteet {:urakka-id harja-urakka-id}))

(defn rakenna-tieosoitteet [kohteet]
  {:tieosoitteet
   (into []
         (flatten
           (mapv (fn [kohde]
                   (let [tr (:tierekisteriosoitevali kohde)]
                     [{:tunniste (str "kohde-" (:tunnus kohde) "-alku")
                       :tie (:tienumero tr)
                       :osa (:aosa tr)
                       :etaisyys (:aet tr)
                       :ajorata (:ajorata tr)}
                      {:tunniste (str "kohde-" (:tunnus kohde) "-loppu")
                       :tie (:tienumero tr)
                       :osa (:losa tr)
                       :etaisyys (:let tr)
                       :ajorata (:ajorata tr)}
                      (mapv (fn [alikohde]
                              (let [tr (:tierekisteriosoitevali alikohde)]
                                [{:tunniste (str "alikohde-" (:tunnus kohde) "-" (:tunnus alikohde) "-alku" )
                                  :tie (:tienumero tr)
                                  :osa (:aosa tr)
                                  :etaisyys (:aet tr)
                                  :ajorata (:ajorata tr)}
                                 {:tunniste (str "alikohde-" (:tunnus kohde) "-" (:tunnus alikohde) "-loppu")
                                  :tie (:tienumero tr)
                                  :osa (:losa tr)
                                  :etaisyys (:let tr)
                                  :ajorata (:ajorata tr)}]))
                            (:alikohteet kohde))]))
                 kohteet)))})

(defn yhdista-yha-ja-vkm-kohteet [yha-kohteet vkm-kohteet]
  ;; esimerkki vkm:n palauttamista kohteista
  #_ {"tieosoitteet" [{"ajorata" 0, "palautusarvo" 1, "osa" 1, "etaisyys" 1, "tie" 20, "tunniste" "kohde-string-alku"}
                      {"palautusarvo" 0, "virheteksti" "Tieosoitteelle ei saatu historiatietoa.", "tunniste" "kohde-string-loppu"}
                      {"palautusarvo" 0, "virheteksti" "Tieosoitteelle ei saatu historiatietoa.", "tunniste" "alikohde-string-A-alku"}
                      {"palautusarvo" 0,
                       "virheteksti" "Tieosoitteelle ei saatu historiatietoa.",
                       "tunniste" "alikohde-string-A-loppu"}]}

  ;; esimerkki yha:n palauttamista kohteista
  #_[{:alikohteet [{:paallystystoimenpide {:kokonaismassamaara 124,
                                           :kuulamylly 4,
                                           :paallystetyomenetelma 22,
                                           :raekoko 12,
                                           :rc-prosentti 14,
                                           :uusi-paallyste 11},
                    :tierekisteriosoitevali {:aet 3,
                                             :ajorata 0,
                                             :aosa 3,
                                             :kaista 11,
                                             :karttapaivamaara #inst"2015-12-31T22:00:00.000-00:00",
                                             :let 3,
                                             :losa 3,
                                             :tienumero 3},
                    :tunnus "A",
                    :yha-id 3}],
      :keskimaarainen-vuorokausiliikenne 1000,
      :kohdetyyppi :paikkaus,
      :nykyinen-paallyste 1,
      :tierekisteriosoitevali {:aet 3,
                               :ajorata 0,
                               :aosa 3,
                               :kaista 11,
                               :karttapaivamaara #inst"2015-12-31T22:00:00.000-00:00",
                               :let 3,
                               :losa 3,
                               :tienumero 3},
      :tunnus "string",
      :yha-id 5,
      :yllapitoluokka 1}]

  ;; todo: toteuta yhdistäminen!
  yha-kohteet)

(defn paivita-yha-kohteet
  "Hakee YHA-kohteet, päivittää ne kutsumalla VMK-palvelua ja tallentaa ne Harjan kantaan.
   Suoritus tapahtuu asynkronisesti"
  ([harja-urakka-id] (paivita-yha-kohteet harja-urakka-id {}))
  ([harja-urakka-id optiot]
    ; FIXME Melkoinen If-hirviö, miten tätä siistisi?
   (go (let [uudet-yha-kohteet (<! (hae-yha-kohteet harja-urakka-id))
             _ (log "[YHA] Uudet YHA-kohteet: " (pr-str uudet-yha-kohteet))]
         (if (k/virhe? uudet-yha-kohteet)
           (viesti/nayta! "Kohteiden haku YHA:sta epäonnistui" :warning viesti/viestin-nayttoaika-keskipitka)
           (if (> (count uudet-yha-kohteet) 0)
             (let [tieosoitteet (rakenna-tieosoitteet uudet-yha-kohteet)
                   tilanne-pvm (:karttapaivamaara (:tierekisteriosoitevali (first uudet-yha-kohteet)))
                   vkm-kohteet (<! (vkm/muunna-tierekisteriosoitteet-eri-paivan-verkolle tieosoitteet tilanne-pvm (pvm/nyt)))]
               (if (k/virhe? vkm-kohteet)
                 (viesti/nayta! "YHA:n kohteiden päivittäminen viitekehysmuuntimella epäonnistui." :warning viesti/viestin-nayttoaika-keskipitka)
                 (let [kohteet (yhdista-yha-ja-vkm-kohteet uudet-yha-kohteet vkm-kohteet)
                       yhatiedot (<! (tallenna-uudet-yha-kohteet harja-urakka-id kohteet))]
                   (if (k/virhe? yhatiedot)
                     (viesti/nayta! "YHA:n kohteiden tallentaminen epäonnistui." :warning viesti/viestin-nayttoaika-keskipitka)
                     (do
                       (log "[YHA] Kohteet käsitelty, urakan uudet yhatiedot: " (pr-str yhatiedot))
                       (swap! nav/valittu-urakka assoc :yhatiedot yhatiedot))))))
             (when-not (:sidontahaku? optiot)
               (viesti/nayta! "Uusia kohteita ei löytynyt." :success viesti/viestin-nayttoaika-lyhyt))))))))

(defn paivita-kohdeluettelo [urakka oikeus]
  [harja.ui.napit/palvelinkutsu-nappi
   "Hae uudet YHA-kohteet"
   #(do
     (log "[YHA] Päivitetään Harja-urakan " (:id urakka) " kohdeluettelo.")
     (paivita-yha-kohteet (:id urakka)))
   {:luokka "nappi-ensisijainen"
    :disabled (not (oikeudet/on-muu-oikeus? "sido" oikeus (:id urakka) @istunto/kayttaja))
    :virheviesti "Kohdeluettelon päivittäminen epäonnistui."
    :kun-onnistuu (fn [_]
                    (log "[YHA] Kohdeluettelo päivitetty")
                    (swap! nav/valittu-urakka assoc-in [:yhatiedot :kohdeluettelo-paivitetty]
                           (cljs-time.core/to-default-time-zone (t/now))))}])

(defn kohdeluettelo-paivitetty [urakka]
  [:div (str "Kohdeluettelo päivitetty: "
             (if-let [kohdeluettelo-paivitetty (get-in urakka [:yhatiedot :kohdeluettelo-paivitetty])]
               (pvm/pvm-aika kohdeluettelo-paivitetty)
               "ei koskaan"))])
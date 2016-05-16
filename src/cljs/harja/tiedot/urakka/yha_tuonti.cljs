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
            [harja.pvm :as pvm]
            [harja.ui.modal :as modal])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]))

(defn yha-tuontioikeus? [urakka]
  (case (:tyyppi urakka)
    :paallystys
    (oikeudet/on-muu-oikeus? "sido"
                             oikeudet/urakat-kohdeluettelo-paallystyskohteet
                             (:id @nav/valittu-urakka) @istunto/kayttaja)
    :paikkaus
    (oikeudet/on-muu-oikeus? "sido"
                             oikeudet/urakat-kohdeluettelo-paikkauskohteet
                             (:id @nav/valittu-urakka) @istunto/kayttaja)
    false))

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

(defn kohteen-alun-tunnus [kohde]
  (str "kohde-" (:tunnus kohde) "-alku"))

(defn kohteen-lopun-tunnus [kohde]
  (str "kohde-" (:tunnus kohde) "-loppu"))

(defn alikohteen-alun-tunnus [kohde alikohde]
  (str "alikohde-" (:tunnus kohde) "-" (:tunnus alikohde) "-alku"))

(defn alikohteen-lopun-tunnus [kohde alikohde]
  (str "alikohde-" (:tunnus kohde) "-" (:tunnus alikohde) "-loppu"))

(defn rakenna-tieosoitteet [kohteet]
  {:tieosoitteet
   (into []
         (flatten
           (mapv (fn [kohde]
                   (let [tr (:tierekisteriosoitevali kohde)]
                     [{:tunniste (kohteen-alun-tunnus kohde)
                       :tie (:tienumero tr)
                       :osa (:aosa tr)
                       :etaisyys (:aet tr)
                       :ajorata (:ajorata tr)}
                      {:tunniste (kohteen-lopun-tunnus kohde)
                       :tie (:tienumero tr)
                       :osa (:losa tr)
                       :etaisyys (:let tr)
                       :ajorata (:ajorata tr)}
                      (mapv (fn [alikohde]
                              (let [tr (:tierekisteriosoitevali alikohde)]
                                [{:tunniste (alikohteen-alun-tunnus kohde alikohde)
                                  :tie (:tienumero tr)
                                  :osa (:aosa tr)
                                  :etaisyys (:aet tr)
                                  :ajorata (:ajorata tr)}
                                 {:tunniste (alikohteen-lopun-tunnus kohde alikohde)
                                  :tie (:tienumero tr)
                                  :osa (:losa tr)
                                  :etaisyys (:let tr)
                                  :ajorata (:ajorata tr)}]))
                            (:alikohteet kohde))]))
                 kohteet)))})

(defn paivita-osoitteen-osa [kohde osoite avain tunnus]
  (assoc-in kohde [:tierekisteriosoitevali avain]
            (if-let [arvo (get osoite tunnus)]
              arvo
              (get-in kohde [:tierekisteriosoitevali avain]))))

(defn hae-vkm-osoite [vkm-kohteet hakutunnus]
  (first (filter #(= hakutunnus (get % "tunniste")) (get vkm-kohteet "tieosoitteet"))))

(defn paivita-tieosoite [kohde alkuosanosoite loppuosanosoite]
  (-> kohde
      (paivita-osoitteen-osa alkuosanosoite :tienumero "tie")
      (paivita-osoitteen-osa alkuosanosoite :ajorata "ajorata")
      (paivita-osoitteen-osa alkuosanosoite :aet "etaisyys")
      (paivita-osoitteen-osa alkuosanosoite :aosa "osa")
      (paivita-osoitteen-osa loppuosanosoite :let "etaisyys")
      (paivita-osoitteen-osa loppuosanosoite :losa "osa")))

(defn yhdista-yha-ja-vkm-kohteet [yha-kohteet vkm-kohteet]
  (mapv (fn [kohde]
          (let [alkuosanhakutunnus (kohteen-alun-tunnus kohde)
                loppuosanhakutunnus (kohteen-lopun-tunnus kohde)
                alkuosanosoite (hae-vkm-osoite vkm-kohteet alkuosanhakutunnus)
                loppuosanosoite (hae-vkm-osoite vkm-kohteet loppuosanhakutunnus)]
            (-> kohde
                (paivita-tieosoite alkuosanosoite loppuosanosoite)
                (assoc :alikohteet
                       (mapv
                         (fn [alikohde]
                           (let [alkuosanhakutunnus (alikohteen-alun-tunnus kohde alikohde)
                                 loppuosanhakutunnus (alikohteen-lopun-tunnus kohde alikohde)
                                 alkuosanosoite (hae-vkm-osoite vkm-kohteet alkuosanhakutunnus)
                                 loppuosanosoite (hae-vkm-osoite vkm-kohteet loppuosanhakutunnus)]
                             (paivita-tieosoite alikohde alkuosanosoite loppuosanosoite)))
                         (:alikohteet kohde))))))
        yha-kohteet))

(defn- hae-paivita-ja-tallenna-yllapitokohteet
  "Hakee YHA-kohteet, päivittää ne nykyiselle tieverkolle kutsumalla VMK-palvelua (viitekehysmuunnin)
   ja tallentaa ne Harjan kantaan. Palauttaa mapin, jossa tietoja suorituksesta"
  [harja-urakka-id]
  (go (let [uudet-yha-kohteet (<! (hae-yha-kohteet harja-urakka-id))
            _ (log "[YHA] Uudet YHA-kohteet: " (pr-str uudet-yha-kohteet))]
        (if (k/virhe? uudet-yha-kohteet)
          {:status :error :viesti "Kohteiden haku YHA:sta epäonnistui."
           :koodi :kohteiden-haku-yhasta-epaonnistui}
          (if (= (count uudet-yha-kohteet) 0)
            {:status :ok :viesti "Uusia kohteita ei löytynyt." :koodi :ei-uusia-kohteita}
            (let [tieosoitteet (rakenna-tieosoitteet uudet-yha-kohteet)
                  tilanne-pvm (:karttapaivamaara (:tierekisteriosoitevali (first uudet-yha-kohteet)))
                  vkm-kohteet (<! (vkm/muunna-tierekisteriosoitteet-eri-paivan-verkolle tieosoitteet tilanne-pvm (pvm/nyt)))]
              (if (k/virhe? vkm-kohteet)
                {:status :error :viesti "YHA:n kohteiden päivittäminen viitekehysmuuntimella epäonnistui."
                 :koodi :kohteiden-paivittaminen-vmklla-epaonnistui}
                (let [kohteet (yhdista-yha-ja-vkm-kohteet uudet-yha-kohteet vkm-kohteet)
                      yhatiedot (<! (tallenna-uudet-yha-kohteet harja-urakka-id kohteet))]
                  (if (k/virhe? yhatiedot)
                    {:status :error :viesti "Päivitettyjen kohteiden tallentaminen epäonnistui."
                     :koodi :kohteiden-tallentaminen-epaonnistui}
                    ;; FIXME Tarkista epäonnistuneet VKM-kohteet ja palauta mappi:
                    #_{:status :ok :epaonnistuneet-kohteet [] :yhatiedot yhatiedot
                       :koodi :kohteiden-paivittaminen-vmklla-epaonnistui-osittain}
                    {:status :ok :uudet-kohteet (count uudet-yha-kohteet) :yhatiedot yhatiedot
                     :koodi :kohteet-tallennettu})))))))))

(defn- vkm-yhdistamistulos-dialogi [epaonnistuneet-kohteet]
  [:div
   [:p "Seuraavien YHA-kohteiden päivittäminen Harjan käyttämälle tieverkolle viitekehysmuuntimella ei onnistunut. Tarkista kohteiden tiedot YHA:sta ja yritä päivittää kohteet uudestaan."]
   [:ul
    (for [kohde epaonnistuneet-kohteet]
      [:li (:tunniste kohde)])]])

(defn- kasittele-onnistunut-kohteiden-paivitys [vastaus harja-urakka-id optiot]
  ;; Tallenna uudet YHA-tiedot urakalle
  (when (= (:id @nav/valittu-urakka) harja-urakka-id)
    (swap! nav/valittu-urakka assoc :yhatiedot (:yhatiedot vastaus)))

  ;; Näytä ilmoitus tarvittaessa
  (when (and (= (:status vastaus) :ok)
             (= (:koodi vastaus) :ei-uusia-kohteita)
             (:nayta-ilmoitus-ei-uusia-kohteita? optiot))
    (viesti/nayta! (:viesti vastaus) :success viesti/viestin-nayttoaika-lyhyt)))

(defn- kasittele-epaonnistunut-kohteiden-paivitys [vastaus]
  ;; Kohteiden osittain epäonnistunut päivittäminen näytetään modal-dialogissa
  (when (and (= (:status vastaus) :error)
             (= (:koodi vastaus) :kohteiden-paivittaminen-vmklla-epaonnistui-osittain))
    (modal/nayta!
      {:otsikko "Kaikkia kohteita ei voitu käsitellä"
       :footer [:button.nappi-toissijainen {:on-click (fn [e]
                                                        (.preventDefault e)
                                                        (modal/piilota!))}
                "Sulje"]}
      [vkm-yhdistamistulos-dialogi (:epaonnistuneet-kohteet vastaus)]))

  ;; Muut virheet käsitellään perus virheviestinä.
  (when (and (= (:status vastaus) :error)
             (not= (:koodi vastaus) :kohteiden-paivittaminen-vmklla-epaonnistui-osittain))
    (viesti/nayta! (:viesti vastaus) :warning viesti/viestin-nayttoaika-keskipitka)))

(defn paivita-yha-kohteet
  "Päivittää urakalle uudet YHA-kohteet. Suoritus tapahtuu asynkronisesti"
  ([harja-urakka-id] (paivita-yha-kohteet harja-urakka-id {}))
  ([harja-urakka-id optiot]
   (go (let [vastaus (<! (hae-paivita-ja-tallenna-yllapitokohteet harja-urakka-id))]
         (log "[YHA] Kohteet käsitelty, käsittelytiedot: " (pr-str vastaus))
         (if (= (:status vastaus) :ok)
           (kasittele-onnistunut-kohteiden-paivitys vastaus harja-urakka-id optiot)
           (kasittele-epaonnistunut-kohteiden-paivitys vastaus))))))


(defn paivita-kohdeluettelo [urakka oikeus]
  [harja.ui.napit/palvelinkutsu-nappi
   "Hae uudet YHA-kohteet"
   #(do
     (log "[YHA] Päivitetään Harja-urakan " (:id urakka) " kohdeluettelo.")
     (paivita-yha-kohteet (:id urakka) {:nayta-ilmoitus-ei-uusia-kohteita? true}))
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
(ns harja.tiedot.urakka.yhatuonti
  (:require [reagent.core :refer [atom] :as r]
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
            [harja.ui.modal :as modal]
            [harja.ui.ikonit :as ikonit]
            [harja.tiedot.urakka.paallystys :as paallystys]
            [cljs.core.async :as async]
            [harja.ui.napit :as napit]
            [harja.ui.yleiset :as yleiset])
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

(def yha-kohteiden-paivittaminen-kaynnissa? (atom false))

(defn- vkm-yhdistamistulos-dialogi [{:keys [epaonnistuneet-vkm-muunnokset epaonnistuneet-tallennukset]}]
  (let [epaonnistunut-kohde (fn [kohde]
                              (let [tr (:tierekisteriosoitevali kohde)]
                                [:li
                                 "Nimi: " (:nimi kohde) ", "
                                 "YHA-id: " (:yha-id kohde) ", "
                                 "tierekisteriosoiteväli: "
                                 (:tienumero tr) " / " (:aosa tr) " / " (:aet tr) " / " (:losa tr) " / " (:let tr)
                                 " ajorata " (:ajorata tr) " kaista " (:kaista tr) ", "
                                 (when-let [virheet (or (:virheet kohde) (:kohde-epavalidi-syy kohde))]
                                   [:ul
                                    (when-let [alku-virhe (:alku virheet)]
                                      [:li "Alkuosan virheet: " alku-virhe])
                                    (when-let [loppu-virhe (:loppu virheet)]
                                      [:li "Loppuosan virheet: " loppu-virhe])
                                    (when-let [syy (:kohde-epavalidi-syy kohde)]
                                      [:li syy])])]))]
    [:div
     (when-not (empty? epaonnistuneet-vkm-muunnokset)
       [:div
        [:p
         "Seuraavien YHA-kohteiden tierekisteriosoitteiden päivittäminen Harjan käyttämälle tieverkolle viitekehysmuuntimella ei onnistunut.
         Kohteet on kuitenkin tallennettu Harjaan."]
        [:ul
         (for [kohde epaonnistuneet-vkm-muunnokset]
           ^{:key (:yha-id kohde)}
           [epaonnistunut-kohde kohde])]])
     (when-not (empty? epaonnistuneet-tallennukset)
       [:div
        [:p
         "Seuraavien YHA-kohteiden tallentaminen Harjaan epäonnistui:"]
        [:ul
         (for [kohde epaonnistuneet-tallennukset]
           ^{:key (:yha-id kohde)}
           [epaonnistunut-kohde kohde])]])

     [:p "Tarkista kohteiden osoitteet ja varmista, että ne ovat oikein YHA:ssa."]]))

(defn- kasittele-onnistunut-kohteiden-paivitys [vastaus harja-urakka-id optiot]
  ;; Päivitä YHA-tiedot
  (when (and (:yhatiedot vastaus) (= (:id @nav/valittu-urakka) harja-urakka-id))
    (let [{:keys [etunimi sukunimi]} @istunto/kayttaja]
      (nav/paivita-urakan-tiedot! @nav/valittu-urakka-id
                                  #(-> %
                                       (assoc :yhatiedot (:yhatiedot vastaus))
                                       (update :yhatiedot merge
                                               {:kohdeluettelo-paivitetty (pvm/nyt)
                                                :kohdeluettelo-paivittaja-etunimi etunimi
                                                :kohdeluettelo-paivittaja-sukunimi sukunimi})))))

  ;; Näytä ilmoitus tarvittaessa
  (when (and (= (:status vastaus) :ok)
             (= (:koodi vastaus) :ei-uusia-kohteita)
             (:nayta-ilmoitus-ei-uusia-kohteita? optiot))
    (viesti/nayta! (:viesti vastaus) :success viesti/viestin-nayttoaika-lyhyt)))

(defn- kasittele-epaonnistunut-kohteiden-paivitys [vastaus harja-urakka-id]
  ;; Päivitä YHA-tiedot
  (when (and (:yhatiedot vastaus) (= (:id @nav/valittu-urakka) harja-urakka-id))
    (nav/paivita-urakan-tiedot! @nav/valittu-urakka-id assoc :yhatiedot (:yhatiedot vastaus)))

  ;; Tunnetut virhetilanteet näytetään modal-dialogissa
  (cond (and (= (:status vastaus) :error)
             (= (:koodi vastaus) :vkm-muunnos-epaonnistui-osittain))
        (modal/nayta!
          {:otsikko "Kaikkia kohteita ei voitu käsitellä"
           :footer [napit/sulje #(modal/piilota!)]}
          [vkm-yhdistamistulos-dialogi {:epaonnistuneet-vkm-muunnokset (:epaonnistuneet-vkm-muunnokset vastaus)}])

        (and (= (:status vastaus) :error)
             (= (:koodi vastaus) :kohteiden-tallentaminen-epaonnistui-osittain))
        (modal/nayta!
          {:otsikko "Kaikkia kohteita ei voitu käsitellä"
           :footer [napit/sulje #(modal/piilota!)]}
          [vkm-yhdistamistulos-dialogi {:epaonnistuneet-tallennukset (:epaonnistuneet-tallennukset vastaus)}])

        (and (= (:status vastaus) :error)
             (= (:koodi vastaus) :vkm-muunnos-ja-kohteiden-tallentaminen-epaonnistui-osittain))
        (modal/nayta!
          {:otsikko "Kaikkia kohteita ei voitu käsitellä"
           :footer [napit/sulje #(modal/piilota!)]}
          [vkm-yhdistamistulos-dialogi {:epaonnistuneet-vkm-muunnokset (:epaonnistuneet-vkm-muunnokset vastaus)
                                        :epaonnistuneet-tallennukset (:epaonnistuneet-tallennukset vastaus)}])

        ;; Muut (odottamattomat) virheet käsitellään perus virheviestinä.
        :default
        (when (and (= (:status vastaus) :error)
                   (not= (:koodi vastaus) :vkm-muunnos-epaonnistui-osittain))
          (viesti/nayta! (:viesti vastaus) :warning viesti/viestin-nayttoaika-keskipitka))))

(defn paivita-yha-kohteet
  "Päivittää urakalle uudet YHA-kohteet. Suoritus tapahtuu asynkronisesti"
  ([harja-urakka-id] (paivita-yha-kohteet harja-urakka-id {}))
  ([harja-urakka-id optiot]
   (go
     (reset! yha-kohteiden-paivittaminen-kaynnissa? true)
     (let [vastaus (<! (k/post! :paivita-yha-kohteet {:urakka-id harja-urakka-id}))]
       (log "[YHA] Kohteet käsitelty, käsittelytiedot: " (pr-str vastaus))
       (reset! yha-kohteiden-paivittaminen-kaynnissa? false)
       (if (= (:status vastaus) :ok)
         (kasittele-onnistunut-kohteiden-paivitys vastaus harja-urakka-id optiot)
         (kasittele-epaonnistunut-kohteiden-paivitys vastaus harja-urakka-id))
       vastaus))))

(def +yha-tuonnin-vihje+
  "Uusien kohteiden tuominen ei poista Harjaan tehtyjä muutoksia jo aiemmin tuotuihin kohteisiin. Vain mahdolliset uudet kohteet tuodaan YHA:sta Harjaan.")

(defn paivita-kohdeluettelo [urakka oikeus]
  (r/with-let [progress (r/atom {})]
    (tarkkaile! "PROGRESS: " progress)
    [:div
     [napit/palvelinkutsu-nappi
      "Hae uudet YHA-kohteet"
      #(paivita-yha-kohteet (:id urakka) {:nayta-ilmoitus-ei-uusia-kohteita? true})
      {:luokka "nappi-ensisijainen"
       :disabled (or
                   @yha-kohteiden-paivittaminen-kaynnissa?
                   (not (oikeudet/on-muu-oikeus? "sido" oikeus (:id urakka) @istunto/kayttaja)))
       :virheviesti "Kohdeluettelon päivittäminen epäonnistui."
       :kun-valmis (fn [_] (reset! progress {}))
       :kun-onnistuu (fn [_]
                       (log "[YHA] Kohdeluettelo päivitetty"))}]
     [yleiset/vihje +yha-tuonnin-vihje+]
     (let [{:keys [progress viesti max]} @progress]
       (when progress
         [:div
          [:progress {:value progress :max max}]
          viesti]))]))

(defn kohdeluettelo-paivitetty [urakka]
  (when-not @yha-kohteiden-paivittaminen-kaynnissa?
    (let [yhatiedot (:yhatiedot urakka)
          kohdeluettelo-paivitetty (:kohdeluettelo-paivitetty yhatiedot)
          paivittajan-etunimi (:kohdeluettelo-paivittaja-etunimi yhatiedot)
          paivittajan-sukunimi (:kohdeluettelo-paivittaja-sukunimi yhatiedot)
          paivittajan-nimi (str " (" paivittajan-etunimi " " paivittajan-sukunimi ")")]
      [:div (str "Kohdeluettelo päivitetty: "
                 (if kohdeluettelo-paivitetty
                   (str (pvm/pvm-aika kohdeluettelo-paivitetty)
                        (when (or paivittajan-etunimi paivittajan-sukunimi)
                          paivittajan-nimi))
                   "ei koskaan"))])))

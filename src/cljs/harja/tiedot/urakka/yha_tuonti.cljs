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
            [harja.ui.modal :as modal]
            [harja.ui.ikonit :as ikonit]
            [harja.tiedot.urakka.paallystys :as paallystys]
            [cljs.core.async :as async])
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
  (str "kohde-" (:yha-id kohde) "-alku"))

(defn kohteen-lopun-tunnus [kohde]
  (str "kohde-" (:yha-id kohde) "-loppu"))

(defn alikohteen-alun-tunnus [kohde alikohde]
  (str "alikohde-" (:yha-id kohde) "-" (:yha-id alikohde) "-alku"))

(defn alikohteen-lopun-tunnus [kohde alikohde]
  (str "alikohde-" (:yha-id kohde) "-" (:yha-id alikohde) "-loppu"))

(defn rakenna-tieosoitteet [kohteet]
  (into []
        (mapcat (fn [kohde]
                  (let [tr (:tierekisteriosoitevali kohde)]
                    (concat
                      [{:tunniste (kohteen-alun-tunnus kohde)
                        :tie (:tienumero tr)
                        :osa (:aosa tr)
                        :etaisyys (:aet tr)
                        :ajorata (:ajorata tr)}
                       {:tunniste (kohteen-lopun-tunnus kohde)
                        :tie (:tienumero tr)
                        :osa (:losa tr)
                        :etaisyys (:let tr)
                        :ajorata (:ajorata tr)}]
                      (mapcat (fn [alikohde]
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
                              (:alikohteet kohde)))))
                kohteet)))

(defn paivita-osoitteen-osa [kohde osoite avain tunnus]
  (assoc-in kohde [:tierekisteriosoitevali avain]
            (or (get osoite tunnus)
                (get-in kohde [:tierekisteriosoitevali avain]))))

(defn hae-vkm-osoite [vkm-kohteet hakutunnus]
  (first (filter #(= hakutunnus (get % "tunniste")) (get vkm-kohteet "tieosoitteet"))))

(defn paivita-kohde [kohde alkuosanosoite loppuosanosoite virhe?]
  (if virhe?
    (assoc kohde :virhe true)
    (-> kohde
        (paivita-osoitteen-osa alkuosanosoite :tienumero "tie")
        (paivita-osoitteen-osa alkuosanosoite :ajorata "ajorata")
        (paivita-osoitteen-osa alkuosanosoite :aet "etaisyys")
        (paivita-osoitteen-osa alkuosanosoite :aosa "osa")
        (paivita-osoitteen-osa loppuosanosoite :let "etaisyys")
        (paivita-osoitteen-osa loppuosanosoite :losa "osa"))))

(defn vkm-virhe? [hakutunnus vkm-kohteet]
  (some #(and (= hakutunnus (get % "tunniste"))
              (not (= 1 (get % "palautusarvo"))))
        (get vkm-kohteet "tieosoitteet")))

(defn yhdista-yha-ja-vkm-kohteet [yha-kohteet vkm-kohteet]
  (mapv (fn [kohde]
          (let [alkuosanhakutunnus (kohteen-alun-tunnus kohde)
                loppuosanhakutunnus (kohteen-lopun-tunnus kohde)
                alkuosanosoite (hae-vkm-osoite vkm-kohteet alkuosanhakutunnus)
                loppuosanosoite (hae-vkm-osoite vkm-kohteet loppuosanhakutunnus)
                virhe? (or (vkm-virhe? alkuosanhakutunnus vkm-kohteet)
                           (vkm-virhe? loppuosanhakutunnus vkm-kohteet))]
            (-> kohde
                (paivita-kohde alkuosanosoite loppuosanosoite virhe?)
                (assoc :alikohteet
                       (mapv
                         (fn [alikohde]
                           (let [alkuosanhakutunnus (alikohteen-alun-tunnus kohde alikohde)
                                 loppuosanhakutunnus (alikohteen-lopun-tunnus kohde alikohde)
                                 alkuosanosoite (hae-vkm-osoite vkm-kohteet alkuosanhakutunnus)
                                 loppuosanosoite (hae-vkm-osoite vkm-kohteet loppuosanhakutunnus)]
                             (paivita-kohde alikohde alkuosanosoite loppuosanosoite virhe?)))
                         (:alikohteet kohde))))))
        yha-kohteet))

(def yha-kohteiden-paivittaminen-kaynnissa? (atom false))

(defn hae-vkm-kohteet [tieosoitteet tilanne-pvm]
  ;; VKM-muuntimelle annetaan tieosoitteet URL-parametreinä.
  ;; Jotta URL:n pituus ei kasva liian isoksi, käsitellään osoitteet 10 kpl. erissä.
  (let [tieosoiteerat (partition-all 10 tieosoitteet)
        kanavat (mapv
                  #(vkm/muunna-tierekisteriosoitteet-eri-paivan-verkolle
                     {:tieosoitteet %}
                     tilanne-pvm
                     (pvm/nyt))
                  tieosoiteerat)
        kanavat (async/merge kanavat)]
    
    (go (loop [acc []
               v (<! kanavat)]
          (if (nil? v)
            (vec acc)
            (recur (concat acc v) (<! kanavat)))))))

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
            (let [_ (log "[YHA] Tehdään VKM-haku")
                  tieosoitteet (rakenna-tieosoitteet uudet-yha-kohteet)
                  tilanne-pvm (:karttapaivamaara (:tierekisteriosoitevali (first uudet-yha-kohteet)))
                  vkm-kohteet (hae-vkm-kohteet tieosoitteet tilanne-pvm)]
              (log "[YHA] VKM-kohteet: " (pr-str vkm-kohteet))
              (if (k/virhe? vkm-kohteet)
                {:status :error :viesti "YHA:n kohteiden päivittäminen viitekehysmuuntimella epäonnistui."
                 :koodi :kohteiden-paivittaminen-vmklla-epaonnistui}
                (let [_ (log "[YHA] Yhdistetään VKM-kohteet")
                      kohteet (yhdista-yha-ja-vkm-kohteet uudet-yha-kohteet vkm-kohteet)
                      epaonnistuneet-kohteet (vec (filter :virhe kohteet))
                      _ (log "[YHA] Tallennetaan uudet kohteet:" (pr-str kohteet))
                      yhatiedot (<! (tallenna-uudet-yha-kohteet harja-urakka-id kohteet))]
                  (if (k/virhe? yhatiedot)
                    {:status :error :viesti "Päivitettyjen kohteiden tallentaminen epäonnistui."
                     :koodi :kohteiden-tallentaminen-epaonnistui}
                    (if (empty? epaonnistuneet-kohteet)
                      {:status :ok :uudet-kohteet (count uudet-yha-kohteet) :yhatiedot yhatiedot
                       :koodi :kohteet-tallennettu}
                      {:status :error :epaonnistuneet-kohteet epaonnistuneet-kohteet :yhatiedot yhatiedot
                       :koodi :kohteiden-paivittaminen-vmklla-epaonnistui-osittain}))))))))))

(defn- vkm-yhdistamistulos-dialogi [epaonnistuneet-kohteet]
  [:div
   [:p
    "Seuraavien YHA-kohteiden tierekisteriosoitteiden päivittäminen Harjan käyttämälle tieverkolle viitekehysmuuntimella ei onnistunut. "
    "Tarkista kohteiden osoitteet ja varmista, että ne ovat oikein."]
   [:ul
    (for [kohde epaonnistuneet-kohteet]
      (let [tr (:tierekisteriosoitevali kohde)]
        [:li
         "Nimi: " (:nimi kohde)
         ", YHA id: " (:yha-id kohde)
         ", tierekisteriosoiteväli: " (:tienumero tr) " / " (:aosa tr) " / " (:aet tr) " / " (:losa tr) " / " (:let tr)]))]])

(defn- kasittele-onnistunut-kohteiden-paivitys [vastaus harja-urakka-id optiot]
  ;; Tallenna uudet YHA-tiedot urakalle
  (when (and (:yhatiedot vastaus) (= (:id @nav/valittu-urakka) harja-urakka-id))
    (nav/paivita-urakan-tiedot! @nav/valittu-urakka-id assoc :yhatiedot (:yhatiedot vastaus)))

  ;; Näytä ilmoitus tarvittaessa
  (when (and (= (:status vastaus) :ok)
             (= (:koodi vastaus) :ei-uusia-kohteita)
             (:nayta-ilmoitus-ei-uusia-kohteita? optiot))
    (viesti/nayta! (:viesti vastaus) :success viesti/viestin-nayttoaika-lyhyt)))

(defn- kasittele-epaonnistunut-kohteiden-paivitys [vastaus harja-urakka-id]
  (when (and
          (some? (:yhatiedot vastaus))
          (= (:id @nav/valittu-urakka) harja-urakka-id))
    (nav/paivita-urakan-tiedot! @nav/valittu-urakka-id assoc :yhatiedot (:yhatiedot vastaus)))

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
   (go
     (reset! yha-kohteiden-paivittaminen-kaynnissa? true)
     (let [vastaus (<! (hae-paivita-ja-tallenna-yllapitokohteet harja-urakka-id))]
       (log "[YHA] Kohteet käsitelty, käsittelytiedot: " (pr-str vastaus))
       (reset! yha-kohteiden-paivittaminen-kaynnissa? false)
       (if (= (:status vastaus) :ok)
         (kasittele-onnistunut-kohteiden-paivitys vastaus harja-urakka-id optiot)
         (kasittele-epaonnistunut-kohteiden-paivitys vastaus harja-urakka-id))))))

(defn paivita-kohdeluettelo [urakka oikeus]
  (when-not @yha-kohteiden-paivittaminen-kaynnissa?
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
                      (nav/paivita-urakan-tiedot! @nav/valittu-urakka-id assoc-in [:yhatiedot :kohdeluettelo-paivitetty]
                                                  (pvm/nyt)))}]))

(defn kohdeluettelo-paivitetty [urakka]
  (if @yha-kohteiden-paivittaminen-kaynnissa?
    [ajax-loader "Kohteiden päivitys käynnissä"]
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

(defn yha-lahetysnappi [oikeus urakka-id sopimus-id vuosi paallystysilmoitukset]
  (let [ilmoituksen-voi-lahettaa? (fn [paallystysilmoitus]
                                    (and (= :hyvaksytty (:paatos-tekninen-osa paallystysilmoitus))
                                         (or (= :valmis (:tila paallystysilmoitus))
                                             (= :lukittu (:tila paallystysilmoitus)))))
        lahetettavat-ilmoitukset (filter ilmoituksen-voi-lahettaa? paallystysilmoitukset)
        kohde-idt (mapv :paallystyskohde-id lahetettavat-ilmoitukset)]
    (when-not @yha-kohteiden-paivittaminen-kaynnissa?
      [harja.ui.napit/palvelinkutsu-nappi
       (if (= 1 (count paallystysilmoitukset))
         (ikonit/teksti-ja-ikoni "Lähetä" (ikonit/livicon-arrow-right))
         "Lähetä kaikki kohteet YHA:n")
       #(do
          (log "[YHA] Lähetetään urakan (id:" urakka-id ") sopimuksen (id: " sopimus-id ") kohteet (id:t" (pr-str kohde-idt) ") YHA:n")
          (reset! paallystys/kohteet-yha-lahetyksessa kohde-idt)
          (k/post! :laheta-kohteet-yhaan {:urakka-id urakka-id
                                          :sopimus-id sopimus-id
                                          :kohde-idt kohde-idt
                                          :vuosi vuosi}))
       {:luokka "nappi-grid nappi-ensisijainen"
        :disabled (or (not (empty? @paallystys/kohteet-yha-lahetyksessa))
                      (empty? kohde-idt)
                      (not (oikeudet/on-muu-oikeus? "sido" oikeus urakka-id @istunto/kayttaja)))
        :virheviestin-nayttoaika viesti/viestin-nayttoaika-pitka
        :virheviesti "Lähetys epäonnistui. Epäonnistuneiden päällystysilmoitusten lukko avattiin mahdollista muokkausta varten."
        :kun-valmis #(reset! paallystys/kohteet-yha-lahetyksessa nil)
        :kun-onnistuu (fn [paivitetyt-ilmoitukset]
                        (if (every? #(or (:lahetys-onnistunut %) (nil? (:lahetys-onnistunut %))) paivitetyt-ilmoitukset)
                          (do (log "[YHA] Kohteet lähetetty YHA:n. Päivitetyt ilmoitukset: " (pr-str paivitetyt-ilmoitukset))
                              (viesti/nayta! "Kohteet lähetetty onnistuneesti." :success))
                          (do (log "[YHA] Lähetys epäonnistui osalle kohteista YHA:n. Päivitetyt ilmoitukset: " (pr-str paivitetyt-ilmoitukset))
                              (viesti/nayta! "Lähetys epäonnistui osalle kohteista. Tarkista kohteiden tiedot." :warning)))
                        (reset! paallystys/paallystysilmoitukset paivitetyt-ilmoitukset))}])))
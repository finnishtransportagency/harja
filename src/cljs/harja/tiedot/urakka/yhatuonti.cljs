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
  (first (filter #(= hakutunnus (get % "tunniste")) vkm-kohteet)))

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
        vkm-kohteet))

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

(defn hae-vkm-kohteet [tieosoitteet tilanne-pvm progress-fn]
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
            (let [{tieosoitteet "tieosoitteet"} v]
              (progress-fn (count tieosoitteet))
              (recur (into acc tieosoitteet) (<! kanavat))))))))

(defn- hae-paivita-ja-tallenna-yllapitokohteet
  "Hakee YHA-kohteet, päivittää ne nykyiselle tieverkolle kutsumalla VMK-palvelua (viitekehysmuunnin)
   ja tallentaa ne Harjan kantaan. Palauttaa mapin, jossa tietoja suorituksesta"
  [harja-urakka-id progress-fn]
  (go
    (progress-fn {:progress 0 :viesti "Haetaan kohteet YHA:sta"})
    (let [uudet-yha-kohteet (<! (hae-yha-kohteet harja-urakka-id))
          _ (log "[YHA] Uudet YHA-kohteet: " (pr-str uudet-yha-kohteet))]
      (if (k/virhe? uudet-yha-kohteet)
        {:status :error :viesti "Kohteiden haku YHA:sta epäonnistui."
         :koodi :kohteiden-haku-yhasta-epaonnistui}
        (if (= (count uudet-yha-kohteet) 0)
          {:status :ok :viesti "Uusia kohteita ei löytynyt." :koodi :ei-uusia-kohteita}
          (let [_ (log "[YHA] Tehdään VKM-haku")
                tieosoitteet (rakenna-tieosoitteet uudet-yha-kohteet)
                _ (progress-fn {:progress 1 :max (inc (count tieosoitteet))
                                :viesti "Haetaan tierekisteriosoitteet"})
                tilanne-pvm (:karttapaivamaara (:tierekisteriosoitevali (first uudet-yha-kohteet)))
                vkm-kohteet (<! (hae-vkm-kohteet tieosoitteet tilanne-pvm progress-fn))]
            (log "[YHA] VKM-kohteet: " (pr-str vkm-kohteet))
            (if (k/virhe? vkm-kohteet)
              {:status :error :viesti "YHA:n kohteiden päivittäminen viitekehysmuuntimella epäonnistui."
               :koodi :kohteiden-paivittaminen-vmklla-epaonnistui}
              (let [_ (log "[YHA] Yhdistetään VKM-kohteet")
                    yhdistetyt-kohteet (yhdista-yha-ja-vkm-kohteet uudet-yha-kohteet vkm-kohteet)
                    yhdistyksessa-epaonnistuneet-kohteet (filterv :virhe yhdistetyt-kohteet)
                    yhdistyksessa-onnistuneet-kohteet (filterv (comp not :virhe) yhdistetyt-kohteet)
                    _ (log "[YHA] Tallennetaan uudet kohteet:" (pr-str yhdistyksessa-onnistuneet-kohteet))
                    {:keys [yhatiedot tallentamatta-jaaneet-kohteet] :as vastaus}
                    (<! (tallenna-uudet-yha-kohteet harja-urakka-id yhdistetyt-kohteet))]
                (if (k/virhe? vastaus)
                  {:status :error :viesti "Kohteiden tallentaminen epäonnistui."
                   :koodi :kohteiden-tallentaminen-epaonnistui}
                  (cond
                    ;; VKM-muunnoksessa tuli ongelma, mutta muuten kohteet saatiin tallennettua
                    (and (not (empty? yhdistyksessa-epaonnistuneet-kohteet))
                         (empty? tallentamatta-jaaneet-kohteet))
                    {:status :error
                     :epaonnistuneet-vkm-muunnokset yhdistyksessa-epaonnistuneet-kohteet
                     :yhatiedot yhatiedot
                     :koodi :vkm-muunnos-epaonnistui-osittain}

                    ;; VKM-muunnos oli OK, mutta kohteiden tallennuksessa tuli ongelma
                    (and (empty? yhdistyksessa-epaonnistuneet-kohteet)
                         (not (empty? tallentamatta-jaaneet-kohteet)))
                    {:status :error
                     :epaonnistuneet-tallennukset tallentamatta-jaaneet-kohteet
                     :yhatiedot yhatiedot
                     :koodi :kohteiden-tallentaminen-epaonnistui-osittain}

                    ;; VKM-muunnoksessa oli ongelmia, kuin myös kohteiden tallennuksessa
                    (and (not (empty? yhdistyksessa-epaonnistuneet-kohteet))
                         (not (empty? tallentamatta-jaaneet-kohteet)))
                    {:status :error
                     :epaonnistuneet-vkm-muunnokset yhdistyksessa-epaonnistuneet-kohteet
                     :epaonnistuneet-tallennukset tallentamatta-jaaneet-kohteet
                     :yhatiedot yhatiedot
                     :koodi :vkm-muunnos-ja-kohteiden-tallentaminen-epaonnistui-osittain}

                    ;; Kaikki kohteet saatiin muunnettua ja tallennettua onnistuneesti
                    :default
                    {:status :ok
                     :uudet-kohteet (count uudet-yha-kohteet)
                     :yhatiedot yhatiedot
                     :koodi :kohteet-tallennettu}))))))))))

(defn- vkm-yhdistamistulos-dialogi [{:keys [epaonnistuneet-vkm-muunnokset epaonnistuneet-tallennukset]}]
  (let [epaonnistunut-kohde (fn [kohde]
                              (let [tr (:tierekisteriosoitevali kohde)]
                                [:li
                                 "Nimi: " (:nimi kohde) ", "
                                 "YHA-id: " (:yha-id kohde) ", "
                                 "tierekisteriosoiteväli: "
                                 (:tienumero tr) " / " (:aosa tr) " / " (:aet tr) " / " (:losa tr) " / " (:let tr)
                                 " ajorata " (:ajorata tr) " kaista " (:kaista tr) ", "
                                 (when-let [syy (:kohde-epavalidi-syy kohde)]
                                   syy)]))]
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
  ([harja-urakka-id] (paivita-yha-kohteet harja-urakka-id {} (constantly nil)))
  ([harja-urakka-id optiot]
   (paivita-yha-kohteet harja-urakka-id optiot (constantly nil)))
  ([harja-urakka-id optiot progress-fn]
   (go
     (reset! yha-kohteiden-paivittaminen-kaynnissa? true)
     (let [vastaus (<! (hae-paivita-ja-tallenna-yllapitokohteet harja-urakka-id progress-fn))]
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
      #(paivita-yha-kohteet (:id urakka) {:nayta-ilmoitus-ei-uusia-kohteita? true}
                            (fn [p]
                              (if (map? p)
                                (swap! progress merge p)
                                (swap! progress update :progress + p))))
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

(defn yha-lahetysnappi [{:keys [oikeus urakka-id sopimus-id vuosi paallystysilmoitukset
                                lahetys-kaynnissa-fn kun-onnistuu kun-virhe kohteet-yha-lahetyksessa]}]
  (let [ilmoituksen-voi-lahettaa? (fn [paallystysilmoitus]
                                    (and (= :hyvaksytty (:paatos-tekninen-osa paallystysilmoitus))
                                         (or (= :valmis (:tila paallystysilmoitus))
                                             (= :lukittu (:tila paallystysilmoitus)))))
        lahetettavat-ilmoitukset (filter ilmoituksen-voi-lahettaa? paallystysilmoitukset)
        kohde-idt (mapv :paallystyskohde-id lahetettavat-ilmoitukset)]
    (when-not @yha-kohteiden-paivittaminen-kaynnissa?
      [napit/palvelinkutsu-nappi
       (if (= 1 (count paallystysilmoitukset))
         (ikonit/teksti-ja-ikoni "Lähetä" (ikonit/livicon-arrow-right))
         "Lähetä kaikki kohteet YHAan")
       #(do
          (log "[YHA] Lähetetään urakan (id:" urakka-id ") sopimuksen (id: " sopimus-id ") kohteet (id:t" (pr-str kohde-idt) ") YHA:n")
          (lahetys-kaynnissa-fn kohde-idt)
          (k/post! :laheta-kohteet-yhaan {:urakka-id urakka-id
                                          :sopimus-id sopimus-id
                                          :kohde-idt kohde-idt
                                          :vuosi vuosi}
                   nil
                   true))
       {:luokka "nappi-grid nappi-ensisijainen"
        :disabled (or (not (empty? kohteet-yha-lahetyksessa))
                      (empty? kohde-idt)
                      (not (oikeudet/on-muu-oikeus? "sido" oikeus urakka-id @istunto/kayttaja)))
        :virheviestin-nayttoaika viesti/viestin-nayttoaika-pitka
        :kun-valmis #(do
                       ;; Tämä on jätetty tähän, koska paallystysilmoitukset atomia käytetään muuallakin kuin
                       ;; Päällystysilmoituksissa
                       (reset! paallystys/paallystysilmoitukset (:paallystysilmoitukset %))
                       (lahetys-kaynnissa-fn nil))
        :kun-onnistuu (fn [vastaus]
                        (kun-onnistuu (:paallystysilmoitukset vastaus)))
        :kun-virhe (fn [vastaus]
                     (log "[YHA] Lähetys epäonnistui osalle kohteista YHAan. Vastaus: " (pr-str vastaus))
                     (kun-virhe vastaus))
        :nayta-virheviesti? false
        :virheviesti "Ylläpitokohteen lähettäminen YHAan epäonnistui teknisen virheen takia. Yritä myöhemmin uudestaan
                      tai ota yhteyttä Harjan asiakastukeen."}])))

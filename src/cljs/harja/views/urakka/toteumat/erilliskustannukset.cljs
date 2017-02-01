(ns harja.views.urakka.toteumat.erilliskustannukset
  "Urakan 'Toteumat' välilehden Erilliskustannuksien osio"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.modal :as modal]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko +korostuksen-kesto+]]
            [harja.ui.napit :as napit]
            [harja.ui.viesti :as viesti]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.indeksit :as i]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.suunnittelu :as s]
            [harja.tiedot.urakka.toteumat :as toteumat]
            [harja.views.urakka.valinnat :as valinnat]

            [harja.ui.lomake :refer [lomake]]
            [harja.loki :refer [log logt]]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [cljs.core.async :refer [<! >! chan]]
            [cljs.core.async :refer [<! timeout]]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.domain.skeema :refer [+tyotyypit+]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.urakka :as urakka])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))

(defonce valittu-kustannus (atom nil))

(defn tallenna-erilliskustannus [muokattu]
  (go (let [urakka-id (:id @nav/valittu-urakka)
            sopimus-id (first (:sopimus muokattu))
            tpi-id (:tpi_id (:toimenpideinstanssi muokattu))
            tyyppi (name (:tyyppi muokattu))
            indeksi (if (or (not (urakka/indeksi-kaytossa?))
                            (= yleiset/+ei-sidota-indeksiin+ (:indeksin_nimi muokattu)))
                      nil
                      (:indeksin_nimi muokattu))
            rahasumma (if (= (:maksaja muokattu) :urakoitsija)
                        (- (:rahasumma muokattu))
                        (:rahasumma muokattu))
            vanhat-idt (into #{} (map #(:id %) @u/erilliskustannukset-hoitokaudella))
            res (<! (toteumat/tallenna-erilliskustannus (assoc muokattu
                                                          :urakka-id (:id @nav/valittu-urakka)
                                                          :alkupvm (first @u/valittu-hoitokausi)
                                                          :loppupvm (second @u/valittu-hoitokausi)
                                                          :urakka urakka-id
                                                          :sopimus sopimus-id
                                                          :toimenpideinstanssi tpi-id
                                                          :tyyppi tyyppi
                                                          :rahasumma rahasumma
                                                          :indeksin_nimi indeksi)))
            uuden-id (:id (first (filter #(not (vanhat-idt (:id %)))
                                         res)))]
        (reset! u/erilliskustannukset-hoitokaudella res)
        (or uuden-id true))))

(def +valitse-tyyppi+
  "- Valitse tyyppi -")

(defn erilliskustannustyypin-teksti [avainsana]
  "Erilliskustannustyypin teksti avainsanaa vastaan"
  (case avainsana
    :asiakastyytyvaisyysbonus "As.tyyt.\u00ADbonus"
    :muu "Muu"
    +valitse-tyyppi+))

(defn luo-kustannustyypit [urakkatyyppi kayttaja]
  ;; Ei sallita urakoitsijan antaa itselleen asiakastyytyväisyysbonuksia
  (filter #(if (= "urakoitsija" (get-in kayttaja [:organisaatio :tyyppi]))
            (not= :asiakastyytyvaisyysbonus %)
            true)
          (case urakkatyyppi
            :hoito [:asiakastyytyvaisyysbonus :muu]

            :default
            [:asiakastyytyvaisyysbonus :muu])))

(defn maksajavalinnan-teksti [avain]
  (case avain
    :tilaaja "Tilaaja"
    :urakoitsija "Urakoitsija"
    "Maksajaa ei asetettu"))

(defn maksajavalinnat
  [ek]
  (case (:tyyppi ek)
    :asiakastyytyvaisyysbonus
    [:tilaaja]

    :muu
    [:tilaaja :urakoitsija]

    [:tilaaja :urakoitsija]))


(def korostettavan-rivin-id (atom nil))

(defn korosta-rivia
  ([id] (korosta-rivia id +korostuksen-kesto+))
  ([id kesto]
   (reset! korostettavan-rivin-id id)
   (go (<! (timeout kesto))
       (reset! korostettavan-rivin-id nil))))

(def +rivin-luokka+ "korosta")

(defn aseta-rivin-luokka [korostettavan-rivin-id]
  (fn [rivi]
    (if (= korostettavan-rivin-id (:id rivi))
      +rivin-luokka+
      "")))

(defn erilliskustannusten-toteuman-muokkaus
  "Erilliskustannuksen muokkaaminen ja lisääminen"
  []
  (let [ur @nav/valittu-urakka
        urakan-indeksi @u/urakassa-kaytetty-indeksi
        muokattu (atom (if (:id @valittu-kustannus)
                         (assoc @valittu-kustannus
                           :sopimus @u/valittu-sopimusnumero
                           :toimenpideinstanssi @u/valittu-toimenpideinstanssi
                           ;; jos maksaja on urakoitsija, rahasumma kannassa miinusmerkkisenä
                           :maksaja (if (neg? (:rahasumma @valittu-kustannus))
                                      :urakoitsija
                                      :tilaaja)
                           :rahasumma (Math/abs (:rahasumma @valittu-kustannus)))
                         (assoc @valittu-kustannus
                           :sopimus @u/valittu-sopimusnumero
                           :toimenpideinstanssi @u/valittu-toimenpideinstanssi
                           :maksaja :tilaaja
                           :indeksin_nimi yleiset/+ei-sidota-indeksiin+)))
        valmis-tallennettavaksi? (reaction (let [m @muokattu]
                                             (and
                                                    (:toimenpideinstanssi m)
                                                    (:tyyppi m)
                                                    (:pvm m)
                                                    (:rahasumma m))))
        tallennus-kaynnissa (atom false)]

    (komp/luo
      (fn []
        [:div.erilliskustannuksen-tiedot
         [napit/takaisin " Takaisin kustannusluetteloon" #(reset! valittu-kustannus nil)]
         [lomake {:otsikko (if (:id @valittu-kustannus)
                             "Muokkaa kustannusta"
                             "Luo uusi kustannus")
                  :muokkaa! (fn [uusi]
                              (log "MUOKATAAN " (pr-str uusi))
                              (reset! muokattu uusi))
                  :voi-muokata? (oikeudet/voi-kirjoittaa? oikeudet/urakat-toteumat-erilliskustannukset (:id @nav/valittu-urakka))
                  :footer [:span
                           [napit/palvelinkutsu-nappi
                            " Tallenna kustannus"
                            #(tallenna-erilliskustannus @muokattu)
                            {:luokka "nappi-ensisijainen"
                             :disabled (or (not @valmis-tallennettavaksi?)
                                           (not (oikeudet/voi-kirjoittaa? oikeudet/urakat-toteumat-erilliskustannukset (:id @nav/valittu-urakka))))
                             :kun-onnistuu #(let [muokatun-id (or (:id @muokattu) %)]
                                             (do
                                               (korosta-rivia muokatun-id)
                                               (reset! tallennus-kaynnissa false)
                                               (reset! valittu-kustannus nil)))
                             :kun-virhe (reset! tallennus-kaynnissa false)}]
                           (when (and
                                   (oikeudet/voi-kirjoittaa? oikeudet/urakat-toteumat-erilliskustannukset (:id ur))
                                   (:id @muokattu))
                             [:button.nappi-kielteinen
                              {:class (when @tallennus-kaynnissa "disabled")
                               :on-click
                               (fn [e]
                                 (.preventDefault e)
                                 (yleiset/varmista-kayttajalta
                                   {:otsikko "Erilliskustannuksen poistaminen"
                                    :sisalto (str "Haluatko varmasti poistaa erilliskustannuksen "
                                                  (Math/abs (:rahasumma @muokattu)) "€ päivämäärällä "
                                                  (pvm/pvm (:pvm @muokattu)) "?")
                                    :hyvaksy "Poista"
                                    :hyvaksy-ikoni (ikonit/livicon-trash)
                                    :hyvaksy-napin-luokka "nappi-kielteinen"
                                    :toiminto-fn #(go
                                                    (let [res (tallenna-erilliskustannus
                                                                (assoc @muokattu :poistettu true))]
                                                      (when res
                                                        (do (viesti/nayta! "Kustannus poistettu")
                                                            (reset! valittu-kustannus nil)))))}))}
                              (ikonit/livicon-trash) " Poista kustannus"])]}

          [{:otsikko       "Sopimusnumero" :nimi :sopimus
            :pakollinen?   true
            :tyyppi        :valinta
            :valinta-nayta second
            :valinnat      (:sopimukset ur)
            :fmt           second
            :palstoja 1}
           {:otsikko       "Toimenpide" :nimi :toimenpideinstanssi
            :pakollinen?   true
            :tyyppi        :valinta
            :valinta-nayta #(:tpi_nimi %)
            :valinnat      @u/urakan-toimenpideinstanssit
            :fmt           #(:tpi_nimi %)
            :palstoja 1}
           {:otsikko       "Tyyppi" :nimi :tyyppi
            :pakollinen?   true
            :tyyppi        :valinta
            :valinta-nayta #(if (nil? %) +valitse-tyyppi+ (erilliskustannustyypin-teksti %))
            :valinnat      (luo-kustannustyypit (:tyyppi ur) @istunto/kayttaja)
            :fmt           #(erilliskustannustyypin-teksti %)
            :validoi       [[:ei-tyhja "Anna kustannustyyppi"]]
            :palstoja 1
            :aseta         (fn [rivi arvo]
                             (assoc (if (and
                                          urakan-indeksi
                                      (= :asiakastyytyvaisyysbonus arvo))
                               (assoc rivi :indeksin_nimi urakan-indeksi
                                           :maksaja :tilaaja)
                               rivi)
                             :tyyppi arvo))}
           {:otsikko "Toteutunut pvm" :nimi :pvm :tyyppi :pvm
            :pakollinen?   true
            :validoi [[:ei-tyhja "Anna kustannuksen päivämäärä"]]
            :huomauta [[:urakan-aikana-ja-hoitokaudella]]}
           {:otsikko     "Rahamäärä"
            :nimi        :rahasumma
            :pakollinen? true
            :yksikko     "€"
            :tyyppi      :positiivinen-numero
            :validoi     [[:ei-tyhja "Anna rahamäärä"]]
            :palstoja 1}

           (when (urakka/indeksi-kaytossa?)
             {:otsikko     "Indeksi" :nimi :indeksin_nimi :tyyppi :valinta
              :pakollinen? true
              ;; hoitourakoissa as.tyyt.bonuksen laskennan indeksi menee urakan alkamisvuoden mukaan
              :muokattava? #(not (and
                                  (= :asiakastyytyvaisyysbonus (:tyyppi @muokattu))
                                  (= :hoito (:tyyppi ur))))
              :valinnat    (conj @i/indeksien-nimet yleiset/+ei-sidota-indeksiin+)
              :fmt         #(if (nil? %)
                              yleiset/+valitse-indeksi+
                              (str %))
              :palstoja 1
              :vihje       (when (and
                                  (= :asiakastyytyvaisyysbonus (:tyyppi @muokattu))
                                  (= :hoito (:tyyppi ur)))
                             (str "Asiakastyytyväisyysbonuksen indeksitarkistus lasketaan"
                                  " automaattisesti laskutusyhteenvedossa. Käytettävä indeksi"
                                  " määräytyy urakan kilpailuttamisajankohdan perusteella."))})

           ;; asiakastyytyväisyysbonuksen voi maksaa vain tilaaja
           {:otsikko       "Maksaja" :nimi :maksaja :tyyppi :valinta
            :muokattava?   #(not= :asiakastyytyvaisyysbonus (:tyyppi @muokattu))
            :pakollinen?   true
            :valinta-nayta #(maksajavalinnan-teksti %)
            :valinnat-fn   #(maksajavalinnat @muokattu)
            :fmt           #(maksajavalinnan-teksti %)
            :palstoja      1
            }
           {:otsikko     "Lisätieto" :nimi :lisatieto :tyyppi :text :pituus-max 1024
            :placeholder "Kirjoita tähän lisätietoa" :koko [80 :auto]}
           ]

          @muokattu]]))))

(defn erilliskustannusten-toteumalistaus
  "Erilliskustannusten toteumat"
  []
  (let [urakka @nav/valittu-urakka
        valitut-kustannukset
        (reaction (let [[sopimus-id _] @u/valittu-sopimusnumero
                        toimenpideinstanssi (:tpi_id @u/valittu-toimenpideinstanssi)]
                    (reverse (sort-by :pvm (filter #(and
                                                     (= sopimus-id (:sopimus %))
                                                     (= (:toimenpideinstanssi %) toimenpideinstanssi))
                                                   @u/erilliskustannukset-hoitokaudella)))))]

    (komp/luo
      (komp/lippu toteumat/erilliskustannukset-nakymassa?)
      (fn []
        (let [aseta-rivin-luokka (aseta-rivin-luokka @korostettavan-rivin-id)
              oikeus? (oikeudet/voi-kirjoittaa?
                       oikeudet/urakat-toteumat-erilliskustannukset
                       (:id @nav/valittu-urakka))]
          [:div.erilliskustannusten-toteumat
           [valinnat/urakan-sopimus-ja-hoitokausi-ja-toimenpide urakka]
           (yleiset/wrap-if
            (not oikeus?)
            [yleiset/tooltip {} :%
             (oikeudet/oikeuden-puute-kuvaus :kirjoitus
                                             oikeudet/urakat-toteumat-erilliskustannukset)]
            [napit/uusi "Lisää kustannus" #(reset! valittu-kustannus {:pvm (pvm/nyt)})
             {:disabled (not oikeus?)}])

           [grid/grid
            {:otsikko       (str "Erilliskustannukset ")
             :tyhja         (if (nil? @valitut-kustannukset)
                              [ajax-loader "Erilliskustannuksia haetaan..."]
                              "Ei erilliskustannuksia saatavilla.")
             :rivi-klikattu #(reset! valittu-kustannus %)
             :rivin-luokka  #(aseta-rivin-luokka %)}
            [{:otsikko "Tyyppi" :nimi :tyyppi :fmt erilliskustannustyypin-teksti :leveys "17%"}
             {:otsikko "Pvm" :tyyppi :pvm :fmt pvm/pvm :nimi :pvm :leveys "13%"}
             {:otsikko "Raha\u00ADmäärä (€)" :tyyppi :string :nimi :rahasumma :tasaa :oikea
              :hae #(Math/abs (:rahasumma %)) :fmt fmt/euro-opt :leveys "12%"}
             {:otsikko "Indeksi\u00ADkorjattuna (€)" :tyyppi :string :nimi :indeksikorjattuna :tasaa :oikea
              :hae     #(if (nil? (:indeksin_nimi %))
                         "Ei sidottu indeksiin"
                         (if (and
                               (not (nil? (:indeksin_nimi %)))
                               (nil? (:indeksikorjattuna %)))
                           nil
                           (fmt/euro-opt (Math/abs (:indeksikorjattuna %)))))
              :fmt     #(if (nil? %)
                         [:span.ei-arvoa "Ei indeksiarvoa"]
                         (str %))
              :leveys  "13%"}
             {:otsikko "Indeksi" :nimi :indeksin_nimi :leveys "10%"}
             {:otsikko "Mak\u00ADsaja" :tyyppi :string :nimi :maksaja
              :hae     #(if (neg? (:rahasumma %)) "Urakoitsija" "Tilaaja") :leveys "10%"}
             {:otsikko "Lisä\u00ADtieto" :nimi :lisatieto :leveys "35%" :pituus-max 1024}]
            @valitut-kustannukset]])))))

(defn erilliskustannusten-toteumat []
  (fn []
    [:span
     (if @valittu-kustannus
       [erilliskustannusten-toteuman-muokkaus]
       [erilliskustannusten-toteumalistaus])]))

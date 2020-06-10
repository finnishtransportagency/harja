(ns harja.views.urakka.toteumat.erilliskustannukset
  "Urakan 'Toteumat' välilehden Erilliskustannuksien osio"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.varmista-kayttajalta :as varmista-kayttajalta]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko +korostuksen-kesto+]]
            [harja.ui.napit :as napit]
            [harja.ui.viesti :as viesti]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.hallinta.indeksit :as i]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.suunnittelu :as s]
            [harja.tiedot.urakka.toteumat :as toteumat]
            [harja.views.urakka.valinnat :as valinnat]
            [harja.ui.valinnat :as ui-valinnat]

            [harja.ui.lomake :refer [lomake]]
            [harja.loki :refer [log logt]]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [cljs.core.async :refer [<! >! chan]]
            [cljs.core.async :refer [<! timeout]]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.domain.skeema :refer [+tyotyypit+]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.urakka :as urakka-domain]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.urakka :as urakka]
            [harja.asiakas.kommunikaatio :as k])
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
                                                          :indeksin_nimi indeksi)))]

        (if (k/virhe? res)
          (viesti/nayta! "Tallennus epäonnistui!" :danger)
          (let [uuden-id (:id (first (filter #(not (vanhat-idt (:id %)))
                                             res)))]
            (reset! u/erilliskustannukset-hoitokaudella res)
            (or uuden-id true))))))

(def +valitse-tyyppi+
  "- Valitse tyyppi -")

(defn erilliskustannustyypin-teksti [avainsana]
  "Erilliskustannustyypin teksti avainsanaa vastaan"
  (case avainsana
    :asiakastyytyvaisyysbonus "As.tyyt.\u00ADbonus"
    :muu "Muu"
    :alihankintabonus "Alihankintabonus"
    :tavoitepalkkio "Tavoitepalkkio"
    :lupausbonus "Lupausbonus"
    +valitse-tyyppi+))

(defn luo-kustannustyypit [urakkatyyppi kayttaja toimenpideinstanssi]
  ;; Ei sallita urakoitsijan antaa itselleen bonuksia
  ;; Eikä sallita teiden-hoito tyyppisille urakoille kaikkia bonustyyppejä valita miten halutaan vaan hallinnollisille
  ;; toimenpiteille on omat bonukset ja muille toimenpideinstansseille on vain "muu" erilliskustannus
  (filter #(if (= "urakoitsija" (get-in kayttaja [:organisaatio :tyyppi]))
             (= :muu %)
             true)
          (cond
            (= :hoito urakkatyyppi)
            [:asiakastyytyvaisyysbonus :muu]
            (and (= :teiden-hoito urakkatyyppi) (= "23150" (:t2_koodi toimenpideinstanssi)))
            [:asiakastyytyvaisyysbonus :alihankintabonus :tavoitepalkkio :lupausbonus]
            (and (= :teiden-hoito urakkatyyppi) (not= "23150" (:t2_koodi toimenpideinstanssi)))
            [:muu]
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

(defn- mhu-bonuksen-indeksi [bonus-tyyppi urakan-indeksi]
  (if (or
        (= :asiakastyytyvaisyysbonus bonus-tyyppi)
        (= :lupausbonus bonus-tyyppi))
    urakan-indeksi
    yleiset/+ei-sidota-indeksiin+))

(defn erilliskustannusten-toteuman-muokkaus
  "Erilliskustannuksen muokkaaminen ja lisääminen"
  []
  (let [ur @nav/valittu-urakka
        urakan-indeksi (:indeksi ur)
        urakan-tyyppi (:tyyppi ur)
        muokattu (atom (if (:id @valittu-kustannus)
                         (assoc @valittu-kustannus
                           :sopimus @u/valittu-sopimusnumero
                           :toimenpideinstanssi (if (= "Kaikki" (:tpi_nimi @u/valittu-toimenpideinstanssi))
                                                  (u/urakan-toimenpideinstanssi-tpille (:toimenpideinstanssi @valittu-kustannus))
                                                  @u/valittu-toimenpideinstanssi)
                           ;; jos maksaja on urakoitsija, rahasumma kannassa miinusmerkkisenä
                           :maksaja (if (neg? (:rahasumma @valittu-kustannus))
                                      :urakoitsija
                                      :tilaaja)
                           :rahasumma (Math/abs (:rahasumma @valittu-kustannus)))
                         (assoc @valittu-kustannus
                           :sopimus @u/valittu-sopimusnumero
                           :toimenpideinstanssi (if (= "Kaikki" (:tpi_nimi @u/valittu-toimenpideinstanssi))
                                                  (first @u/urakan-toimenpideinstanssit)
                                                  @u/valittu-toimenpideinstanssi)
                           :maksaja :tilaaja
                           :indeksin_nimi yleiset/+ei-sidota-indeksiin+)))
        valmis-tallennettavaksi? (reaction (let [m @muokattu]
                                             (and
                                               (:toimenpideinstanssi m)
                                               (:tyyppi m)
                                               (:pvm m)
                                               (:rahasumma m))))
        tallennus-kaynnissa (atom false)
        valittavat-indeksit (map :indeksinimi (i/urakkatyypin-indeksit (:tyyppi ur)))]
    (komp/luo
      (fn []
        [:div.erilliskustannuksen-tiedot
         [napit/takaisin " Takaisin kustannusluetteloon" #(reset! valittu-kustannus nil)]
         [lomake {:otsikko (if (:id @valittu-kustannus)
                             "Muokkaa kustannusta"
                             "Luo uusi kustannus")
                  :muokkaa! (fn [uusi]
                              (reset! muokattu uusi))
                  :voi-muokata? (if (urakka-domain/vesivaylaurakka? @nav/valittu-urakka)
                                  (oikeudet/voi-kirjoittaa? oikeudet/urakat-toteumat-vesivaylaerilliskustannukset (:id @nav/valittu-urakka))
                                  (oikeudet/voi-kirjoittaa? oikeudet/urakat-toteumat-erilliskustannukset (:id @nav/valittu-urakka)))
                  :footer [:span
                           [napit/palvelinkutsu-nappi
                            " Tallenna kustannus"
                            #(tallenna-erilliskustannus @muokattu)
                            {:luokka "nappi-ensisijainen"
                             :disabled (or (not @valmis-tallennettavaksi?)
                                           (not (if (urakka-domain/vesivaylaurakka? @nav/valittu-urakka)
                                                  (oikeudet/voi-kirjoittaa? oikeudet/urakat-toteumat-vesivaylaerilliskustannukset (:id @nav/valittu-urakka))
                                                  (oikeudet/voi-kirjoittaa? oikeudet/urakat-toteumat-erilliskustannukset (:id @nav/valittu-urakka)))))
                             :kun-onnistuu #(let [muokatun-id (or (:id @muokattu) %)]
                                              (do
                                                (korosta-rivia muokatun-id)
                                                (reset! tallennus-kaynnissa false)
                                                (reset! valittu-kustannus nil)))
                             :kun-virhe (reset! tallennus-kaynnissa false)}]
                           (when (and
                                   (if (urakka-domain/vesivaylaurakka? @nav/valittu-urakka)
                                     (oikeudet/voi-kirjoittaa? oikeudet/urakat-toteumat-vesivaylaerilliskustannukset (:id ur))
                                     (oikeudet/voi-kirjoittaa? oikeudet/urakat-toteumat-erilliskustannukset (:id ur)))
                                   (:id @muokattu))
                             [:button.nappi-kielteinen
                              {:class (when @tallennus-kaynnissa "disabled")
                               :on-click
                               (fn [e]
                                 (.preventDefault e)
                                 (varmista-kayttajalta/varmista-kayttajalta
                                   {:otsikko "Erilliskustannuksen poistaminen"
                                    :sisalto (str "Haluatko varmasti poistaa erilliskustannuksen "
                                                  (Math/abs (:rahasumma @muokattu)) "€ päivämäärällä "
                                                  (pvm/pvm (:pvm @muokattu)) "?")
                                    :hyvaksy "Poista"
                                    :toiminto-fn #(go
                                                    (let [res (tallenna-erilliskustannus
                                                                (assoc @muokattu :poistettu true))]
                                                      (when res
                                                        (do (viesti/nayta! "Kustannus poistettu")
                                                            (reset! valittu-kustannus nil)))))}))}
                              (ikonit/livicon-trash) " Poista kustannus"])]}

          [{:otsikko "Sopimusnumero" :nimi :sopimus
            :pakollinen? true
            :tyyppi :valinta
            :valinta-nayta second
            :valinnat (:sopimukset ur)
            :fmt second
            :palstoja 1}
           {:otsikko "Toimenpide" :nimi :toimenpideinstanssi
            :pakollinen? true
            :tyyppi :valinta
            :valinta-nayta #(:tpi_nimi %)
            :valinnat @u/urakan-toimenpideinstanssit
            :fmt #(:tpi_nimi %)
            :palstoja 1
            :aseta (fn
                     ;; MHU (:teiden-hoito) tyyppisillä urakoilla on rajoituksia erilliskustatannustyypeissä.
                     ;; Jos urakan-tyyppi :teiden-hoito ja toimenpideinstanssi "hoidonjohto" 23150, niin annetaan mahdollisuus bonusten lisäykselle.
                     ;; Muuten :teiden-hoito tyyppisillä urakoilla on mahdollisuu lisätä vain "muu" tyyppinen erilliskustannus
                     ;; Jottenka tässä vain nollataan tyypin valinta, jos tähän toimenpideinstanssiin kosketaan
                     [rivi arvo]
                     (assoc
                       (if (= :teiden-hoito urakan-tyyppi)
                         (assoc rivi :tyyppi nil)
                         rivi)
                       :toimenpideinstanssi arvo))}
           {:otsikko "Tyyppi" :nimi :tyyppi
            :pakollinen? true
            :tyyppi :valinta
            :valitse-ainoa? false
            :aseta-vaikka-sama? true
            :valinta-arvo identity
            :valinta-nayta (fn [arvo]
                             (if arvo (erilliskustannustyypin-teksti arvo) +valitse-tyyppi+))
            :valinnat (luo-kustannustyypit (:tyyppi ur) @istunto/kayttaja (:toimenpideinstanssi @muokattu))
            :fmt #(erilliskustannustyypin-teksti %)
            :validoi [[:ei-tyhja "Anna kustannustyyppi"]]
            :palstoja 1
            :aseta (fn [rivi arvo]
                     (assoc (cond
                              (and
                                (not= :teiden-hoito urakan-tyyppi)
                                urakan-indeksi
                                (= :asiakastyytyvaisyysbonus arvo))
                              (assoc rivi :indeksin_nimi urakan-indeksi
                                          :maksaja :tilaaja)
                              (and
                                (= :teiden-hoito urakan-tyyppi)
                                (or (= :lupausbonus arvo)
                                    (= :asiakastyytyvaisyysbonus arvo)))
                              (assoc rivi :indeksin_nimi urakan-indeksi
                                          :maksaja :tilaaja)
                              (and
                                (= :teiden-hoito urakan-tyyppi)
                                (or (not= :lupausbonus arvo)
                                    (not= :asiakastyytyvaisyysbonus arvo)))
                              (assoc rivi :indeksin_nimi "Ei sidota indeksiin"
                                          :maksaja :tilaaja)
                              :default rivi)
                       :tyyppi arvo))}
           {:otsikko "Toteutunut pvm" :nimi :pvm :tyyppi :pvm
            :pakollinen? true
            :validoi [[:ei-tyhja "Anna kustannuksen päivämäärä"]]
            :huomauta [[:urakan-aikana-ja-hoitokaudella]]}
           {:otsikko "Rahamäärä"
            :nimi :rahasumma
            :pakollinen? true
            :yksikko "€"
            :tyyppi :positiivinen-numero
            :validoi [[:ei-tyhja "Anna rahamäärä"]]
            :palstoja 1}

           (when (urakka/indeksi-kaytossa?)
             {:otsikko "Indeksi" :nimi :indeksin_nimi :tyyppi :valinta
              :pakollinen? true
              ;; Hoitourakoissa as.tyyt.bonuksen laskennan indeksi menee urakan alkamisvuoden mukaan - indeksi pakotetaan
              ;; Maanteiden hoitourakoissa (MHU) as.tyyt.bonuksen laskennan indeksi ja lupausbonus menee urakan alkamisvuoden mukaan - indeksi pakotetaan
              ;; Sen sijaan maanteiden hoitourakoissa (MHU) alihankintabonukselle ja tavoitepalkkiolle ei saa valita indeksiä.
              :muokattava? #(not (or
                                   (and
                                     (= :asiakastyytyvaisyysbonus (:tyyppi @muokattu))
                                     (= :hoito (:tyyppi ur)))
                                   (= :teiden-hoito (:tyyppi ur))))
              :valinnat (if (= :teiden-hoito (:tyyppi ur))
                          (mhu-bonuksen-indeksi (:tyyppi @muokattu) urakan-indeksi)
                          (conj valittavat-indeksit yleiset/+ei-sidota-indeksiin+))
              :fmt #(cond
                      (and (= :hoito (:tyyppi ur)) (nil? %))
                      yleiset/+valitse-indeksi+
                      (and (= :teiden-hoito (:tyyppi ur)) (nil? %))
                      (mhu-bonuksen-indeksi (:tyyppi @muokattu) urakan-indeksi)
                      :default (str %))
              :palstoja 1
              :vihje (when (and
                             (= :asiakastyytyvaisyysbonus (:tyyppi @muokattu))
                             (= :hoito (:tyyppi ur)))
                       (str "Asiakastyytyväisyysbonuksen indeksitarkistus lasketaan"
                            " automaattisesti laskutusyhteenvedossa. Käytettävä indeksi"
                            " määräytyy urakan kilpailuttamisajankohdan perusteella."))})

           ;; asiakastyytyväisyysbonuksen voi maksaa vain tilaaja
           {:otsikko "Maksaja" :nimi :maksaja :tyyppi :valinta
            :muokattava? #(not= :asiakastyytyvaisyysbonus (:tyyppi @muokattu))
            :pakollinen? true
            :valinta-nayta #(maksajavalinnan-teksti %)
            :valinnat-fn #(maksajavalinnat @muokattu)
            :fmt #(maksajavalinnan-teksti %)
            :palstoja 1}
           {:otsikko "Lisätieto" :nimi :lisatieto :tyyppi :text :pituus-max 1024
            :placeholder "Kirjoita tähän lisätietoa" :koko [80 8]}]

          @muokattu]]))))

(defn erilliskustannusten-toteumalistaus
  "Erilliskustannusten toteumat"
  [urakka]
  (let [urakka @nav/valittu-urakka
        valitut-kustannukset
        (reaction (let [[sopimus-id _] @u/valittu-sopimusnumero
                        toimenpideinstanssi @u/valittu-toimenpideinstanssi]
                    (when @u/erilliskustannukset-hoitokaudella
                      (reverse (sort-by :pvm (filter #(and
                                                        (= sopimus-id (:sopimus %))
                                                        (or (= (:toimenpideinstanssi %) (:tpi_id toimenpideinstanssi))
                                                            (= (:tpi_nimi toimenpideinstanssi) "Kaikki")))
                                                     @u/erilliskustannukset-hoitokaudella))))))]
    (fn []
      (let [aseta-rivin-luokka (aseta-rivin-luokka @korostettavan-rivin-id)
            oikeus? (if (urakka-domain/vesivaylaurakka? @nav/valittu-urakka)
                      (oikeudet/voi-kirjoittaa?
                        oikeudet/urakat-toteumat-vesivaylaerilliskustannukset
                        (:id @nav/valittu-urakka))

                      (oikeudet/voi-kirjoittaa?
                       oikeudet/urakat-toteumat-erilliskustannukset
                       (:id @nav/valittu-urakka)))]
        [:div.erilliskustannusten-toteumat
         [ui-valinnat/urakkavalinnat {:urakka urakka}
          ^{:key "valinnat"}
          [valinnat/urakan-sopimus-ja-hoitokausi-ja-toimenpide+kaikki urakka]
          (yleiset/wrap-if
            (not oikeus?)
            [yleiset/tooltip {} :%
             (oikeudet/oikeuden-puute-kuvaus :kirjoitus
                                             (if (urakka-domain/vesivaylaurakka? @nav/valittu-urakka)
                                               oikeudet/urakat-toteumat-vesivaylaerilliskustannukset
                                               oikeudet/urakat-toteumat-erilliskustannukset))]
            ^{:key "toiminnot"}
            [ui-valinnat/urakkatoiminnot {:urakka urakka}
             ^{:key "lisaa-kustannus"}
             [napit/uusi "Lisää kustannus" #(reset! valittu-kustannus {:pvm (pvm/nyt)})
              {:disabled (not oikeus?)}]])]

         [grid/grid
          {:otsikko (str "Erilliskustannukset ")
           :tyhja (if (nil? @valitut-kustannukset)
                    [ajax-loader "Erilliskustannuksia haetaan..."]
                    "Ei erilliskustannuksia saatavilla.")
           :rivi-klikattu #(reset! valittu-kustannus %)
           :rivin-luokka #(aseta-rivin-luokka %)}
          [{:otsikko "Tyyppi" :nimi :tyyppi :fmt erilliskustannustyypin-teksti :leveys "17%"}
           {:otsikko "Pvm" :tyyppi :pvm :fmt pvm/pvm :nimi :pvm :leveys "13%"}
           {:otsikko "Raha\u00ADmäärä (€)" :tyyppi :string :nimi :rahasumma :tasaa :oikea
            :hae #(Math/abs (:rahasumma %)) :fmt fmt/euro-opt :leveys "12%"}
           {:otsikko "Indeksi\u00ADkorjattuna (€)" :tyyppi :string :nimi :indeksikorjattuna :tasaa :oikea
            :hae #(if (nil? (:indeksin_nimi %))
                    "Ei sidottu indeksiin"
                    (if (and
                          (not (nil? (:indeksin_nimi %)))
                          (nil? (:indeksikorjattuna %)))
                      nil
                      (fmt/euro-opt (Math/abs (:indeksikorjattuna %)))))
            :fmt #(if (nil? %)
                    [:span.ei-arvoa "Ei indeksiarvoa"]
                    (str %))
            :leveys "13%"}
           {:otsikko "Indeksi" :nimi :indeksin_nimi :leveys "10%"}
           {:otsikko "Mak\u00ADsaja" :tyyppi :string :nimi :maksaja
            :hae #(if (neg? (:rahasumma %)) "Urakoitsija" "Tilaaja") :leveys "10%"}
           {:otsikko "Lisä\u00ADtieto" :nimi :lisatieto :leveys "35%" :pituus-max 1024}]
          @valitut-kustannukset]]))))

(defn erilliskustannusten-toteumat [urakka]
  (komp/luo
    (komp/lippu toteumat/erilliskustannukset-nakymassa?)
    (fn [urakka]
      [:span
       (if @valittu-kustannus
         [erilliskustannusten-toteuman-muokkaus]
         [erilliskustannusten-toteumalistaus urakka])])))

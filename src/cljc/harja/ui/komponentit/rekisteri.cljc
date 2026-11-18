(ns harja.ui.komponentit.rekisteri
  "Komponenttien tarkastelusivun rekisteri: komponenttien kuvaus datana.

  Rekisteri on tarkastelusivun kortin, navigaation ja sopimusruudun yhteinen lähde. Uusi
  komponentti lisätään tänne ensin, ja sen esikatselu liitetään samalla `:id`:llä
  tarkastelusivun esikatselut-kartassa.

  Rekisterissä ei ole renderöintiä, jotta sen eheys voidaan testata JVM:llä.")

(def ankkuri-etuliite "ui-komponenttien-tarkastelu-")

(def ryhmat
  "Navigaation ryhmät esitysjärjestyksessä."
  [{:id :alasvedot :nimi "Alasvedot"}
   {:id :toiminnot :nimi "Toiminnot"}
   {:id :lomakkeet :nimi "Lomakkeet"}
   {:id :gridit :nimi "Gridit"}
   {:id :portatut :nimi "Portatut Bootstrap-komponentit"}])

(def luonteet
  "Mikä komponentti on: yksittäinen shared-komponentti vai niistä koottu kokonaisuus."
  #{:shared-komponentti :shared-rakenne})

(def bootstrap-tilat
  "Komponentin riippuvuus Bootstrapista. Teksti näytetään tarkastelusivulla."
  {:ei-riippuvuutta {:teksti "Ei Bootstrap-riippuvuutta"}
   :osittain-riippuvainen {:teksti "Osittain Bootstrap-riippuvainen"}
   :riippuvainen {:teksti "Bootstrap-riippuvainen"}
   :maarittelematta {:teksti "Bootstrap-tila määrittelemättä"}})

(def komponentit
  [{:id :livi-pudotusvalikko
    :nimi "Livi-pudotusvalikko"
    :ryhma :alasvedot
    :luonne :shared-komponentti
      :rooli :syote
      :slotit #{:arvo :vaihtoehdot}
      :tilat #{:normaali :tyhja :virhe :estetty}
      :sallitut-vanhemmat #{:lomake}
    :bootstrap-tila :riippuvainen
    :kuvaus "Nykyinen tuotannossa käytetty dropdown-sopimus."
    :api "harja.ui.yleiset/pudotusvalikko: otsikko + asetuskartta + vaihtoehtovektori"}
   {:id :nappi
    :nimi "Nappi"
    :ryhma :toiminnot
    :luonne :shared-komponentti
      :rooli :toiminto
      :slotit #{:toiminto}
      :tilat #{:normaali :estetty :lataus}
      :sallitut-vanhemmat #{:lomake}
    :bootstrap-tila :osittain-riippuvainen
    :kuvaus "Yhteinen toimintopainike. Renderöi oman button-elementin, mutta käyttöpaikan :luokka voi tuoda Bootstrapin btn-luokat."
    :api "harja.ui.napit/nappi: teksti + callback + optiot (:data-cy, :disabled, :luokka, :tallennus-kaynnissa?)"}
   {:id :suodatin
    :nimi "Suodatin"
    :ryhma :lomakkeet
    :luonne :shared-komponentti
      :rooli :syote
      :slotit #{:kentta}
      :tilat #{:normaali :tyhja :virhe}
      :sallitut-vanhemmat #{:lomake}
    :bootstrap-tila :ei-riippuvuutta
    :kuvaus "Suodattimen label ja kenttä. Käytössä tarjoushintojen näkymässä."
    :api "harja.ui.komponentit/suodatin, suodatinrivi: {:otsikko :id :data-cy} + kenttä (hiccup tai fn)"}
   {:id :valintaryhma
    :nimi "Valintaryhmä"
    :ryhma :lomakkeet
    :luonne :shared-komponentti
      :rooli :syote
      :slotit #{:arvo :vaihtoehdot}
      :tilat #{:normaali :tyhja :virhe :estetty}
      :sallitut-vanhemmat #{:lomake}
    :bootstrap-tila :ei-riippuvuutta
    :kuvaus "Saavutettava natiivin select-kentän ympärille rakennettu valintaryhmä."
    :api "harja.ui.komponentit/valintaryhma: {:otsikko :id :vaihtoehdot :arvo :vaihda-fn :tyhja-valinta :ohje :virhe :pakollinen? :disabled?}"}
   {:id :tekstikentta
    :nimi "Tekstikenttä"
    :ryhma :lomakkeet
    :luonne :shared-komponentti
    :rooli :syote
    :slotit #{:arvo}
    :tilat #{:normaali :tyhja :estetty}
    :sallitut-vanhemmat #{:lomake :suodatin}
    :bootstrap-tila :osittain-riippuvainen
    :kuvaus "Yksirivinen tekstikenttä. Arvo ja muutoksen käsittely omistaa käyttöpaikka. Lomake-tilassa kenttä saa Bootstrapin form-control-luokan."
    :api "harja.ui.kentat/tee-kentta: {:tyyppi :string :placeholder :elementin-id :disabled? :lomake?} + atomi"}
   {:id :numerokentta
    :nimi "Numerokenttä"
    :ryhma :lomakkeet
    :luonne :shared-komponentti
    :rooli :syote
    :slotit #{:arvo}
    :tilat #{:normaali :tyhja :estetty}
    :sallitut-vanhemmat #{:lomake :suodatin}
    :bootstrap-tila :osittain-riippuvainen
    :kuvaus "Numerokenttä muotoilee arvon ja hyväksyy vain sallitut luvut. Lomake-tilassa kenttä saa Bootstrapin form-control-luokan."
    :api "harja.ui.kentat/tee-kentta: {:tyyppi :numero :desimaalien-maara :kokonaisosan-maara :kokonaisluku? :vaadi-ei-negatiivinen? :elementin-id :lomake?} + atomi"}
   {:id :sahkopostikentta
    :nimi "Sähköpostikenttä"
    :ryhma :lomakkeet
    :luonne :shared-komponentti
    :rooli :syote
    :slotit #{:arvo}
    :tilat #{:normaali :tyhja :estetty}
    :sallitut-vanhemmat #{:lomake :suodatin}
    :bootstrap-tila :osittain-riippuvainen
    :kuvaus "Sähköpostiosoitteen syöttökenttä selaimen omalla tyypillä. Lomake-tilassa kenttä saa Bootstrapin form-control-luokan."
    :api "harja.ui.kentat/tee-kentta: {:tyyppi :email :placeholder :on-focus :on-blur :disabled? :lomake?} + atomi"}
   {:id :puhelinkentta
    :nimi "Puhelinkenttä"
    :ryhma :lomakkeet
    :luonne :shared-komponentti
    :rooli :syote
    :slotit #{:arvo}
    :tilat #{:normaali :tyhja :estetty}
    :sallitut-vanhemmat #{:lomake :suodatin}
    :bootstrap-tila :osittain-riippuvainen
    :kuvaus "Puhelinnumeron syöttökenttä. Lomake-tilassa kenttä saa Bootstrapin form-control-luokan, ellei vayla-tyyli? ole päällä."
    :api "harja.ui.kentat/tee-kentta: {:tyyppi :puhelin :placeholder :pituus :vayla-tyyli? :disabled? :lomake?} + atomi"}
   {:id :valintaruutu
    :nimi "Valintaruutu"
    :ryhma :lomakkeet
    :luonne :shared-komponentti
    :rooli :syote
    :slotit #{:arvo}
    :tilat #{:normaali :tyhja :estetty}
    :sallitut-vanhemmat #{:lomake :suodatin}
    :bootstrap-tila :ei-riippuvuutta
    :kuvaus "Yksittäinen valintaruutu, jonka tila on boolean-atomi tai atomi, jonka arvo on true, false tai ::indeterminate."
    :api "harja.ui.kentat/tee-kentta: {:tyyppi :checkbox :teksti :disabled? :iso-clickalue? :valitse!} + atomi"}
   {:id :radiokentta
    :nimi "Radiokenttä"
    :ryhma :lomakkeet
    :luonne :shared-komponentti
    :rooli :syote
    :slotit #{:arvo :valinnat}
    :tilat #{:normaali :tyhja :estetty}
    :sallitut-vanhemmat #{:lomake :suodatin}
    :bootstrap-tila :ei-riippuvuutta
    :kuvaus "Vaihtoehdoista yksi valitaan radiopainikkeella. Arvo on atomi."
    :api "harja.ui.kentat/tee-kentta: {:tyyppi :radio :valinnat :valinta-nayta :valinta-arvo :disabled?} + atomi"}
   {:id :checkbox-ryhma
    :nimi "Checkbox-ryhmä"
    :ryhma :lomakkeet
    :luonne :shared-komponentti
    :rooli :syote
    :slotit #{:arvo :vaihtoehdot}
    :tilat #{:normaali :tyhja :estetty}
    :sallitut-vanhemmat #{:lomake :suodatin}
    :bootstrap-tila :osittain-riippuvainen
    :kuvaus "Monivalinta, jossa arvo on joukko. Yksi palsta ei käytä Bootstrap-luokkia, useampi palsta käyttää col-sm-*-luokkia."
    :api "harja.ui.kentat/tee-kentta: {:tyyppi :checkbox-group :vaihtoehdot :vaihtoehto-nayta :palstoja :disabloi} + atomi joukolla"}
   {:id :lomake
    :nimi "Lomake"
    :ryhma :lomakkeet
    :luonne :shared-rakenne
      :rooli :lomake
      :slotit #{:kentat :toiminnot :tila}
      :tilat #{:normaali :lataus :virhe :onnistui}
      :sallitut-vanhemmat #{:sivu}
    :bootstrap-tila :ei-riippuvuutta
    :kuvaus "Pieni lomakekokonaisuus, jossa näkymä omistaa arvon ja tallennuksen ehdot."
    :api "harja.ui.komponentit: pino + kenttaryhma + valintaryhma + toimintoryhma + tilaviesti"}
   {:id :rivi
    :nimi "Rivi"
    :ryhma :lomakkeet
    :luonne :shared-komponentti
    :rooli :asettelu
    :slotit #{:sarake}
    :tilat #{:normaali}
    :sallitut-vanhemmat #{:lomake :sivu}
    :bootstrap-tila :riippuvainen
    :kuvaus "Asettelurivi, jossa sarakkeet jakavat tilan tasan koon mukaan. Sarakkeiden koon ja rivin luokan välittää käyttöpaikka."
    :api "harja.ui.yleiset/rivi: {:koko :luokka} + sarakkeet. Koko on numero tai mäppi {:lg :md :xs}."}
   {:id :panel
    :nimi "Panel"
    :ryhma :portatut
    :luonne :shared-komponentti
    :rooli :rakenne
    :slotit #{:otsikko :sisalto}
    :tilat #{:normaali :tyhja}
    :sallitut-vanhemmat #{:sivu}
    :bootstrap-tila :ei-riippuvuutta
    :kuvaus "Yhteensopiva panel-wrapper ilman Bootstrapin panel-markupia."
    :api "harja.ui.bootstrap/panel: [options content] tai [options title content]"}
   {:id :perusgrid
    :nimi "Perusgrid"
    :ryhma :gridit
    :luonne :shared-komponentti
    :rooli :taulukko
    :slotit #{:sarakkeet :rivit}
    :tilat #{:normaali :tyhja}
    :sallitut-vanhemmat #{:sivu}
    :bootstrap-tila :riippuvainen
    :kuvaus "Taulukkonäkymä rivien ja sarakkeiden esittämiseen. Paneelin ja napin luokat ovat vielä Bootstrapin panel- ja btn-xs-luokkia."
    :api "harja.ui.grid/grid: {:otsikko :tyhja :tunniste} + sarakkeet + rivit"}
   {:id :muokkausgrid
    :nimi "Muokkausgrid"
    :ryhma :gridit
    :luonne :shared-komponentti
    :rooli :taulukko
    :slotit #{:sarakkeet :rivit}
    :tilat #{:normaali :tyhja :muokkaus}
    :sallitut-vanhemmat #{:sivu}
    :bootstrap-tila :riippuvainen
    :kuvaus "Muokattava taulukko. Rivien muutokset kulkevat käyttöpaikan muutos-callbackin kautta. Paneelin rakenne on vielä Bootstrapin panel-luokkia."
    :api "harja.ui.grid/muokkaus-grid: {:otsikko :tyhja :voi-muokata? :voi-lisata? :voi-kumota? :muutos} + sarakkeet + rivit-atomi"}
   {:id :tabs
    :nimi "Tabs"
    :ryhma :portatut
    :luonne :shared-komponentti
    :rooli :navigaatio
    :slotit #{:otsikot :sisallot}
    :tilat #{:normaali :ei-kayttooikeutta}
    :sallitut-vanhemmat #{:sivu}
    :bootstrap-tila :ei-riippuvuutta
    :kuvaus "Välilehtien public API säilyy, mutta rakenne ei enää nojaa Bootstrapin nav-luokkiin."
    :api "harja.ui.bootstrap/tabs: {:active :style :classes :on-change} + otsikko/avain/sisältö"}
   {:id :navbar
    :nimi "Navbar"
    :ryhma :portatut
    :luonne :shared-komponentti
    :rooli :navigaatio
    :slotit #{:header :vasen :oikea}
    :tilat #{:normaali :suljettu :avoin}
    :sallitut-vanhemmat #{:sivu}
    :bootstrap-tila :ei-riippuvuutta
    :kuvaus "Navigaation yhteensopiva navbar-wrapper Harjan omilla luokilla."
    :api "harja.ui.bootstrap/navbar: options + header + itemit, :right erottaa oikean reunan"}
   {:id :viesti
    :nimi "Viesti"
    :ryhma :portatut
    :luonne :shared-komponentti
    :rooli :palaute
    :slotit #{:sisalto}
    :tilat #{:onnistuminen :info :varoitus :vaara}
    :sallitut-vanhemmat #{:sivu}
    :bootstrap-tila :ei-riippuvuutta
    :kuvaus "Flash-viesti ilman Bootstrapin modal- ja alert-luokkia. Variantit tulevat Harjan omista luokista."
    :api "harja.ui.viesti/nayta!: viesti [luokka] [kesto], luokka :success, :info, :warning tai :danger"}
   {:id :modal
    :nimi "Modal"
    :ryhma :portatut
    :luonne :shared-komponentti
    :rooli :overlay
    :slotit #{:otsikko :sisalto}
    :tilat #{:avoin :suljettu}
    :sallitut-vanhemmat #{:sivu}
    :bootstrap-tila :riippuvainen
    :kuvaus "Modaali näytetään sivun päätason modal-kontissa. Avaamisen ehdot ja sulkemisen seuraukset omistaa näkymä."
    :api "harja.ui.modal/nayta!: {:otsikko :sulje-fn :leveys} + sisältö. Sulkeminen kutsuu :sulje-fn-funktiota."}
   {:id :dropdown-panel
    :nimi "Dropdown-panel"
    :ryhma :portatut
    :luonne :shared-komponentti
    :rooli :rakenne
    :slotit #{:otsikko :sisalto}
    :tilat #{:auki :kiinni}
    :sallitut-vanhemmat #{:sivu}
    :bootstrap-tila :riippuvainen
    :kuvaus "Otsikolla varustettu paneeli, jonka sisältö näkyy vain auki ollessa. Ei käytössä vielä missään näkymässä."
    :api "harja.ui.bootstrap/dropdown-panel: {:open atom :style} otsikko sisältö"}])

(defn ankkuri
  "Komponentin vakaa juuriankkuri (data-cy). Johdetaan id:stä, ei tallenneta erikseen."
  [{:keys [id]}]
  (str ankkuri-etuliite (name id)))

(defn hae
  "Komponentti id:llä tai nil."
  ([id] (hae komponentit id))
  ([komponentit id]
   (first (filter #(= id (:id %)) komponentit))))

(defn ryhmittele
  "Palauttaa vain ne ryhmät, joissa on komponentteja: [{:id :nimi :komponentit [...]}]."
  ([] (ryhmittele ryhmat komponentit))
  ([ryhmat komponentit]
   (into []
     (keep (fn [{:keys [id] :as ryhma}]
             (when-let [ryhman-komponentit (seq (filter #(= id (:ryhma %)) komponentit))]
               (assoc ryhma :komponentit (vec ryhman-komponentit)))))
     ryhmat)))

(defn virheet
  "Palauttaa rekisterin eheysongelmat tekstinä. Tyhjä lista tarkoittaa, että rekisteri on kunnossa."
  ([] (virheet ryhmat komponentit))
  ([ryhmat komponentit]
   (let [ryhma-idt (set (map :id ryhmat))
         tuplat (fn [avain]
                  (->> komponentit (map avain) frequencies
                    (keep (fn [[arvo maara]] (when (> maara 1) arvo)))))]
     (vec
       (concat
         (for [arvo (tuplat :id)] (str "Tuplaid: " arvo))
         (for [{:keys [id ryhma luonne bootstrap-tila nimi kuvaus api]} komponentit
               virhe [(when-not (contains? ryhma-idt ryhma) (str id ": tuntematon ryhmä " ryhma))
                      (when-not (contains? luonteet luonne) (str id ": tuntematon luonne " luonne))
                      (when-not (contains? bootstrap-tilat bootstrap-tila)
                        (str id ": tuntematon bootstrap-tila " bootstrap-tila))
                      (when-not (every? seq [nimi kuvaus api]) (str id ": nimi, kuvaus tai api puuttuu"))]
               :when virhe]
           virhe))))))

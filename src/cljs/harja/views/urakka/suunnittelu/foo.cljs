(ns harja.views.urakka.suunnittelu.foo
  (:require [reagent.core :as r :refer [atom]]
            [tuck.core :as tuck]
            [harja.ui.komponentti :as komp]
            [harja.ui.ikonit :as ikonit]
            [harja.tyokalut.tuck :as tuck-apurit]

            [harja.ui.taulukko.grid-pohjat :as g-pohjat]
            [harja.ui.taulukko.grid :as grid]
            [harja.ui.taulukko.impl.solu :as solu]
            [harja.ui.taulukko.impl.grid :as impl-grid]
            [harja.ui.taulukko.grid-oletusarvoja :as konf]
            [harja.ui.taulukko.protokollat.grid :as p]
            [harja.ui.taulukko.protokollat.grid-osa :as gop]
            [harja.ui.taulukko :as taulukko]
            [harja.ui.napit :as napit]
            [clojure.string :as clj-str]
            [harja.fmt :as fmt])
  (:require-macros [harja.ui.taulukko.grid :refer [defsolu jarjesta-data triggeroi-seurannat]]))

(defonce ^:mutable e! nil)

(defrecord JarjestaData [otsikko])
(defrecord LisaaRivi [])
(defrecord MuokkaaUudenRivinNimea [nimi])
(defrecord PoistaRivi [tunniste])

(extend-protocol tuck/Event
  JarjestaData
  (process-event [{:keys [otsikko]} {{g :grid} :dynaaminen-taulukko-alempi-api :as app}]
    (when-not (nil? otsikko)
      (let [jarjestys-fn (with-meta (fn [datarivit]
                                      (if (= :rivi otsikko)
                                        ;; Jos järjestetään otsikkosarakkeen mukaan, käytetään aakkosjärjestystä
                                        (sort-by key datarivit)
                                        ;; Jos järjestetään minkään muun sarakkeen mukaan, käytetään rivin yhteensä arvoa ja järkätään ASCENDING järjestyksessä
                                        (sort-by (fn [[_ v]]
                                                   (reduce #(+ %1 (get %2 otsikko))
                                                           0
                                                           v))
                                                 datarivit)))
                                    ;; Annetaan metana tämän funktion nimi, jotta ei tarvitse ylikirjoittaa gridillä jo olevaa järjestysfunktiota, jos kyseessä on saman niminen funktio
                                    {:nimi (str "jarjesta-otsikot-" otsikko)})]
        ;; Halutaan järjestää juurikin dataosia jarjestys-fn mukaisesti. Eli grid, jonka nimi on ::data eikä ::otsikko tai ::yhteenveto osioita.
        ;; ::data osion rajapinnaksi on määritetty :datarivit ja halutaan järjestää kaikki data siinä rajapinnassa, joten syvyydeksi määritetään 0.
        (grid/lisaa-jarjestys-fn-gridiin! g
                                          :datarivit
                                          0
                                          jarjestys-fn)
        ;; Yllä oleva rivi lisäsi tuon funktion, muttei triggeröinyt järjestystä. Tämä rivi triggeröi järjestyksen.
        (grid/jarjesta-grid-data! g :datarivit)))
    ;; Ei haluta muuttaa app statea mitenkään, niin palautetaan vain app
    app)
  LisaaRivi
  (process-event [_ {{:keys [uusi-rivi data]} :dynaaminen-taulukko-alempi-api :as app}]
    ;; Rivillä on fixatut 3 lasta, joten lisätään kaikille niille data
    (let [uuden-rivin-nimi (keyword uusi-rivi)
          rivi-olemassa? (some #(= uuden-rivin-nimi (:rivi %))
                               data)
          seuraava-rivitunnistin (inc (reduce #(max %1 (get %2 ::rivitunnistin)) 0 data))]
      ;; Jos yritetään luoda jo olemassa oleva rivi uudelleen, palautetaan app state ilman muutoksia
      (if rivi-olemassa?
        app
        (-> app
            ;; Lisätään uuden rivin kolme datapointtia
            (update-in [:dynaaminen-taulukko-alempi-api :data]
                       (fn [data]
                         (apply conj data (map (fn [i]
                                                 {:rivi uuden-rivin-nimi
                                                  ::rivitunnistin i})
                                               (range seuraava-rivitunnistin (+ seuraava-rivitunnistin 3))))))
            ;; Tyhjennetään "Lisää rivi" kenttä
            (assoc-in [:dynaaminen-taulukko-alempi-api :uusi-rivi] nil)))))
  MuokkaaUudenRivinNimea
  (process-event [{:keys [nimi]} app]
    (assoc-in app [:dynaaminen-taulukko-alempi-api :uusi-rivi] nimi))
  PoistaRivi
  (process-event [{{:keys [rivin-otsikko]} :tunniste} app]
    (let [poistettavien-rivien-tunnistimet (transduce
                                             (comp (filter #(= (:rivi %) rivin-otsikko))
                                                   (map ::rivitunnistin))
                                             conj
                                             #{}
                                             (get-in app [:dynaaminen-taulukko-alempi-api :data]))]
      (-> app
          (update-in [:dynaaminen-taulukko-alempi-api :data] (fn [data]
                                                               (filterv #(not (= (:rivi %) rivin-otsikko))
                                                                        data)))
          (update-in [:dynaaminen-taulukko-alempi-api :data-disable] dissoc rivin-otsikko)
          (update-in [:dynaaminen-taulukko-alempi-api :kirjoitettu-data] (fn [rivitunnistimien-arvot]
                                                                           (into {}
                                                                                 (remove #(contains? poistettavien-rivien-tunnistimet (key %)))
                                                                                 rivitunnistimien-arvot)))))))

;; Data on vektori mappeja. Pidtään myös Otsikkorivin arvot omalla rivillään
(defonce alkudata {:data [{:rivi :foo :a 1 :b 2 :c 3}
                          {:rivi :foo :a 2 :b 3 :c 4}
                          {:rivi :foo :a 3 :b 4 :c 5}
                          {:rivi :bar :a 10 :b 20 :c 30}
                          {:rivi :bar :a 20 :b 30 :c 40}
                          {:rivi :bar :a 30 :b 40 :c 50}
                          {:rivi :baz :a 400 :b 200 :c 300}
                          {:rivi :baz :a 200 :b 300 :c 400}
                          {:rivi :baz :a 300 :b 400 :c 500}]
                   :gridin-otsikot ["RIVIN NIMI" "A" "B" "C"]})

(def tila (atom {:dynaaminen-taulukko-alempi-api alkudata}))

(defn luo-css-luokat! [luokat]
  (let [tyyli-elementti (.createElement js/document "style")
        tyylit (apply str (interpose " " (map (fn [[luokan-nimi css-maaritelma]]
                                                (str "." luokan-nimi " " css-maaritelma))
                                              luokat)))]
    (set! (.-type tyyli-elementti) "text/css")
    (set! (.-innerHTML tyyli-elementti) tyylit)
    (-> (.getElementsByTagName js/document "head")
        (aget 0)
        (.appendChild tyyli-elementti))))

;; defsolu on makro, jonka avulla voi luoda helposti taulukon hyväksymiä soluja. Perussolut on määritetty
;; harja.ui.taulukko.impl.solu ns:ssa ja sieltä voi ottaa mallia miten solu tulisi toteuttaa. Kumminkin, jos haluaa
;; määritellä vain solun render funktion ilman, että implementoi kaikkia mahdollisia protokollia, voi käyttää defsolu
;; makroa, joka määrittelee perusoletuksilla kyseiset protokollat. Makro luo myös konstruktori metodin, jota voi käyttää
;; perus defrecordin luomien konstruktorien lisäksi. Konstruktorin nimi on käytännössä PascalCasesta muutettu kebab-caseksi.
;; Eli tässä esimerkkitapauksessa konstruktorin nimi on vayla-checkbox. Argumenteiksi annetaan defsolulle määritetyt parametrit.
;; Eli tässä "vaihda-fn" ja "txt". Defsolu käyttää defrecordia.
(defsolu VaylaCheckbox
  [vaihda-fn txt]
  {:pre [(fn? vaihda-fn)]}
  (fn disable-rivit [this]
    ;; solu/taman-derefable on sellainen funktio, jota todennäköisesti kaikissa soluissa halutaan kutsua sillä
    ;; sen avulla saa solun datan. Jos luo solun, jonka ulkonäköä ei ole tarkoitus muuttaa ajon aikana, ei tätä
    ;; funktiota tarvitse kutsua.
    (let [taman-data (solu/taman-derefable this)
          checked? (or @taman-data false)
          osan-id (str (grid/hae-osa this :id))]
      [:div
       [:input.vayla-checkbox {:id osan-id
                               :type "checkbox"
                               :checked checked?
                               :on-change (r/partial (:vaihda-fn this) this)}]
       [:label {:for osan-id} (:txt this)]])))

(defn summa-formatointi [teksti]
  ;; Halutaan aina näyttää jokin numero. Joten jos arvona on jokin "tyjä" arvo, näytetään "0,00". js/isNaN palauttaa false,
  ;; jos "teksti" arvo sisältää pilkun.
  (if (nil? teksti)
    "0,00"
    ;; Korvataan piste erotin pilkulla
    (let [teksti (clj-str/replace (str teksti) "," ".")]
      (if (or (= "" teksti) (js/isNaN teksti))
        "0,00"
        ;; Sallitaan vain kahden desimaalin tarkkuus
        (fmt/desimaaliluku teksti 2 true)))))

(defn summa-formatointi-aktiivinen [teksti]
  ;; Summa-formatointi-aktiivinen eroaa summa-formatointi funktiosta siten, että sallitaan tyhjä ("") arvo
  (let [teksti-ilman-pilkkua (clj-str/replace (str teksti) "," ".")]
    (cond
      (or (nil? teksti) (= "" teksti)) ""
      ;; fmt/desimaaliluku poistaa "turhat" nollat lopusta, niin laitetaan ne takaisin
      (re-matches #".*\.0*$" teksti-ilman-pilkkua) (apply str (fmt/desimaaliluku teksti-ilman-pilkkua nil true)
                                                          (drop 1 (re-find #".*(\.|,)(0*)" teksti)))
      :else (fmt/desimaaliluku teksti-ilman-pilkkua nil true))))

(defn poista-tyhjat [arvo]
  (clj-str/replace arvo #"\s" ""))

(defn paivita-raidat! [g]
  ;; paivita-luokat funktion avulla riville lisätään oikea CSS-luokka, jotta rivin väri muuttuu oikeaksi
  (let [paivita-luokat (fn [luokat odd?]
                         (if odd?
                           (-> luokat
                               (conj "table-default-odd")
                               (disj "table-default-even"))
                           (-> luokat
                               (conj "table-default-even")
                               (disj "table-default-odd"))))]
    ;; Halutaan päivittää raidat kaikkiin näkyviin riveihin
    (loop [[rivi & loput-rivit] (grid/nakyvat-rivit g)
           index 0]
      (if rivi
        (let [rivin-nimi (grid/hae-osa rivi :nimi)]
          (grid/paivita-grid! rivi
                              :parametrit
                              (fn [parametrit]
                                (update parametrit :class (fn [luokat]
                                                            ;; disable-valinta on checkboxin sisätävän rivin nimi.
                                                            ;; Yhteenvetorivi ja checkboxrivi halutaan värjätä samalla
                                                            ;; värillä, joten indexin päivitys jätetään väliin.
                                                            (if (= ::disable-valinta rivin-nimi)
                                                              (paivita-luokat luokat (not (odd? index)))
                                                              (paivita-luokat luokat (odd? index)))))))
          (recur loput-rivit
                 ;; Täällä pitää tehdä sama check kuin muutama rivi ylempänä.
                 (if (= ::disable-valinta rivin-nimi)
                   index
                   (inc index))))))))

;; Määritetään, että miten yhteenvetorivi tulisi muuttaa riviksi, jossa on myös checkbox alla.
;; Toteutuksessa päädytään luomaan taulukko, jonka sisälle luodaan kaksi riviä. Ylemmälle riville
;; laitetaan vanha yhteenvetorivi eri nimellä ja alemmalle riville checkboxin sisältämä rivi
(defn rivi->rivi-disablevalinnalla [disable-rivit?-polku rivi]
  (let [sarakkeiden-maara (count (grid/hae-grid rivi :lapset))]
    (with-meta
      (grid/grid {:alueet [{:sarakkeet [0 1] :rivit [0 2]}]
                  :koko (assoc-in konf/auto
                                  [:rivi :nimet]
                                  {::yhteenveto-auki 0
                                   ::disable-valinta 1})
                  :osat [(-> rivi
                             (grid/aseta-nimi ::yhteenveto-auki)
                             ;; Päivitetään Laajenna solun :auki-alussa? arvo trueksi, jotta solun caret-ikoni päivittyy
                             ;; oikein
                             (grid/paivita-grid! :lapset
                                                 (fn [osat]
                                                   (mapv (fn [osa]
                                                           (if (instance? solu/Laajenna osa)
                                                             (assoc osa :auki-alussa? true)
                                                             osa))
                                                         osat))))
                         (grid/rivi {:osat (vec
                                             (cons (vayla-checkbox (fn [this event]
                                                                     (.preventDefault event)
                                                                     ;; Kun checkboxia painetaan, haetaan solun nykyinen arvo käyttämällä
                                                                     ;; grid/solun-arvo funktiota ja invertoidaan sen tulos
                                                                     (let [disable-rivit? (not (grid/solun-arvo this))]
                                                                       (e! (tuck-apurit/->MuutaTila disable-rivit?-polku disable-rivit?))))
                                                                   "Disabloi rivit")
                                                   ;; Loppuihin sarakkeisiin laitetaan vain tyhjä solu
                                                   (repeatedly (dec sarakkeiden-maara) (fn [] (solu/tyhja)))))
                                     :koko {:seuraa {:seurattava ::otsikko
                                                     :sarakkeet :sama
                                                     :rivit :sama}}
                                     :nimi ::disable-valinta}
                                    [{:sarakkeet [0 sarakkeiden-maara] :rivit [0 1]}])]})
      {:key (str "foo-disable-" rivi)})))

;; Sama operaatio kuin rivi->rivi-disablevalinnalla funktiossa, mutta toisin päin.
;; Eli otetaan pelkästään yhteenvetorivi jolloinka checkboxrivi jää pois.
(defn rivi-disablevalinnalla->rivi [rivi-disablevalinnalla]
  (grid/aseta-nimi (grid/paivita-grid! (grid/get-in-grid rivi-disablevalinnalla [::yhteenveto-auki])
                                       :lapset
                                       (fn [osat]
                                         (mapv (fn [osa]
                                                 (if (instance? solu/Laajenna osa)
                                                   (assoc osa :auki-alussa? false)
                                                   osa))
                                               osat)))
                   ::data-yhteenveto))

(defn rivi-disablevalinnalla!
  [laajennasolu disabletasolla?-polku]
  ;; grid/vaihda-osa! funktio ottaa osan, joka halutaan vaihtaa ja osan, jolla se tulisi vaihtaa.
  ;; Lisäksi uudelle osalle pitää määrittää datan käsittelijät
  (grid/vaihda-osa!
    (grid/vanhempi laajennasolu)
    (partial rivi->rivi-disablevalinnalla disabletasolla?-polku)))

(defn rivi-ilman-disablevalintaa!
  [laajennasolu]
  (grid/vaihda-osa!
    (-> laajennasolu grid/vanhempi grid/vanhempi)
    rivi-disablevalinnalla->rivi))

;; Näytetään checkbox, jolla voi disabloida a sarakkeen inputkentät, jos rivikontti aukaistaan.
;; Sulettaessa, tätä checkboxriviä ei näytetä.
(defn muuta-yhteenvetorivi! [auki? solu rivin-nimi]
  (if auki?
    (rivi-disablevalinnalla! solu
                             [:dynaaminen-taulukko-alempi-api :data-disable rivin-nimi])
    (rivi-ilman-disablevalintaa! solu)))

(defn hoida-rivien-nakyvyys! [auki? laajennasolu polku-dataan]
  (let [aukeamis-polku [:.. :.. 1]
        sulkemis-polku [:.. :.. :.. 1]]
    ;; grid/osa-polusta funktiolle voi polun määrittää samaan tyyliin kuin terminaalissa navigoidaan kansioita.
    ;; Eli halutaan viitata ::data-sisalto osaan kun ollaan aukasemassa rivikonttia. Käytössä on ::data-yhteenveto
    ;; rivin laajenna solu, niin pitää mennä kaksi kertaa puussa ylöspäin, jotta saavutetaan ::data osio.
    ;; ::data osion toinen lapsihan sitten on ::data-sisalto.
    (if auki?
      (do (grid/nayta! (grid/osa-polusta laajennasolu aukeamis-polku))
          (paivita-raidat! (grid/osa-polusta (grid/root laajennasolu) polku-dataan)))
      (do (grid/piillota! (grid/osa-polusta laajennasolu sulkemis-polku))
          (paivita-raidat! (grid/osa-polusta (grid/root laajennasolu) polku-dataan))))))

(defn laajenna-solua-klikattu
  [solu rivin-nimi auki? polku-dataan]
  (muuta-yhteenvetorivi! auki? solu rivin-nimi)
  (hoida-rivien-nakyvyys! auki? solu polku-dataan))

(defn paivita-solun-arvo! [{:keys [paivitettava-asia arvo solu ajettavat-jarejestykset triggeroi-seuranta?]
                            :or {ajettavat-jarejestykset false triggeroi-seuranta? false}}]
  ;; jarjesta-data ja triggeroi-seurannat ovat simppeleitä makroja. Ne vain wrappaa sisällensä jonkin koodinpätkän,
  ;; jonka aikana tehdään makron nimen mukaisia asioita. Eli (jarjesta-data true [ body ]) aiheuttaa sen, että
  ;; body:n ajon aikana data järjestetään. Tämä järjestys heijastuu sitten UI:lle. HUOM! jos "body" sisältää async- tai
  ;; laiskasti suoritettavaa koodia, ei järjestystä välttämättä suoriteta.
  (jarjesta-data ajettavat-jarejestykset
    (triggeroi-seurannat triggeroi-seuranta?
      ;; grid/aseta-rajapinnan-data! ottaa argumentikseen data käsittelijän ja "post" rajapinnan nimen. Loput argumentit
      ;; annetaan rajapinnan funktiolle. Eli tääsä rajapintafunktio saa argumentikseen uuden arvon ja tunnisteen dataan,
      ;; jota ollaan muokkaamassa.
      (grid/aseta-rajapinnan-data!
        (grid/osien-yhteinen-asia solu :datan-kasittelija)
        paivitettava-asia
        arvo
        (grid/solun-asia solu :tunniste-rajapinnan-dataan)))))

(defn tayta-alla-olevat-rivit! [asettajan-nimi rivit-alla arvo]
  (when (and arvo (not (empty? rivit-alla)))
    (doseq [rivi rivit-alla
            :let [a-sarakkeen-solu (grid/get-in-grid rivi [1])]]
      (paivita-solun-arvo! {:paivitettava-asia asettajan-nimi
                            :arvo arvo
                            :solu a-sarakkeen-solu
                            :ajettavat-jarejestykset false
                            :triggeroi-seuranta? false}))))

(defn solujen-disable! [rivit disable?]
  (doseq [rivi rivit
          :let [a-solu (grid/get-in-grid rivi [1])]]
    (grid/paivita-osa! a-solu
                       (fn [solu]
                         (assoc-in solu [:parametrit :disabled?] disable?)))))

(defn jarjesta-fn! []
  (e! (->JarjestaData (grid/hae-osa solu/*this* :nimi))))

(defn dynaaminen-taulukko-alempi-api []
  (let [;; Annetaan tälle tila RAtomiksi kursori
        tila (r/cursor tila [:dynaaminen-taulukko-alempi-api])]
    (komp/luo
      (komp/piirretty (fn [_]
                        (let [dom-id "foo"
                              yhteenveto-grid-rajapinta-asetukset-fn (fn [rivin-otsikko]
                                                                       {:rajapinta (keyword (str "data-yhteenveto-" rivin-otsikko))
                                                                        :solun-polun-pituus 1
                                                                        :jarjestys [^{:nimi :mapit} [:rivin-otsikko :a :b :c :poista]]
                                                                        :datan-kasittely (fn [yhteenveto]
                                                                                           (mapv val
                                                                                                 yhteenveto))
                                                                        :tunnisteen-kasittely (fn [osat _]
                                                                                                (mapv (fn [osa]
                                                                                                        (when (instance? solu/Ikoni osa)
                                                                                                          {:rivin-otsikko rivin-otsikko}))
                                                                                                      (grid/hae-grid osat :lapset)))})
                              aukaistu-yhteenveto-rajapinta-asetukset-fn (fn [yhteenveto-grid-rajapinta-asetukset rajapinta]
                                                                           [yhteenveto-grid-rajapinta-asetukset
                                                                            {:rajapinta rajapinta
                                                                             :solun-polun-pituus 1
                                                                             :datan-kasittely (fn [disable?]
                                                                                                [disable? nil nil nil nil])}])
                              ;;
                              g (grid/grid {:nimi ::root
                                            :dom-id dom-id
                                            :root-fn (fn [] (get-in @tila [:grid]))
                                            :paivita-root! (fn [f]
                                                             (swap! tila
                                                                    (fn [tila]
                                                                      (update-in tila [:grid] f))))
                                            :alueet [{:sarakkeet [0 1] :rivit [0 3]}]
                                            :koko (-> konf/auto
                                                      (assoc-in [:rivi :nimet]
                                                                {::otsikko 0
                                                                 ::data 1
                                                                 ::yhteenveto 2})
                                                      (assoc-in [:rivi :korkeudet] {0 "40px"
                                                                                    2 "40px"}))
                                            :osat [(grid/rivi {:nimi ::otsikko
                                                               :koko (-> konf/livi-oletuskoko
                                                                         (assoc-in [:sarake :leveydet] {0 "9fr"
                                                                                                        4 "1fr"})
                                                                         (assoc-in [:sarake :oletus-leveys] "3fr"))
                                                               :osat (conj (mapv (fn [nimi]
                                                                                   (solu/otsikko {:jarjesta-fn! jarjesta-fn!
                                                                                                  :parametrit {:class #{"table-default" "table-default-header"}}
                                                                                                  :nimi nimi}))
                                                                                 [:rivi :a :b :c])
                                                                           (solu/tyhja #{"table-default" "table-default-header"}))
                                                               :luokat #{"salli-ylipiirtaminen"}}
                                                              [{:sarakkeet [0 5] :rivit [0 1]}])
                                                   (grid/dynamic-grid {:nimi ::data
                                                                       :alueet [{:sarakkeet [0 1] :rivit [0 1]}]
                                                                       :osatunnisteet #(map key %)
                                                                       :koko konf/auto
                                                                       :luokat #{"salli-ylipiirtaminen"}
                                                                       :osien-maara-muuttui! (fn [g _] (paivita-raidat! (grid/osa-polusta (grid/root g) [::data])))
                                                                       :toistettavan-osan-data (fn [rivit]
                                                                                                 rivit)
                                                                       :toistettava-osa (fn [rivit-ryhmiteltyna]
                                                                                          (mapv (fn [[rivi rivien-arvot]]
                                                                                                  (with-meta
                                                                                                    (grid/grid {:alueet [{:sarakkeet [0 1] :rivit [0 2]}]
                                                                                                                :nimi ::datarivi
                                                                                                                :koko (-> konf/auto
                                                                                                                          (assoc-in [:rivi :nimet]
                                                                                                                                    {::data-yhteenveto 0
                                                                                                                                     ::data-sisalto 1}))
                                                                                                                :luokat #{"salli-ylipiirtaminen"}
                                                                                                                :osat [(with-meta (grid/rivi {:nimi ::data-yhteenveto
                                                                                                                                              :koko {:seuraa {:seurattava ::otsikko
                                                                                                                                                              :sarakkeet :sama
                                                                                                                                                              :rivit :sama}}
                                                                                                                                              :osat [(solu/laajenna {:aukaise-fn
                                                                                                                                                                     (fn [this auki?]
                                                                                                                                                                       (laajenna-solua-klikattu this rivi auki? [::data]))
                                                                                                                                                                     :auki-alussa? false
                                                                                                                                                                     :parametrit {:class #{"table-default" "lihavoitu"}}})
                                                                                                                                                     (solu/teksti {:parametrit {:class #{"table-default"}}
                                                                                                                                                                   :fmt summa-formatointi})
                                                                                                                                                     (solu/teksti {:parametrit {:class #{"table-default"}}
                                                                                                                                                                   :fmt summa-formatointi})
                                                                                                                                                     (solu/teksti {:parametrit {:class #{"table-default" "harmaa-teksti"}}
                                                                                                                                                                   :fmt summa-formatointi})
                                                                                                                                                     (solu/ikoni {:nimi "poista"
                                                                                                                                                                  :toiminnot {:on-click (fn [_]
                                                                                                                                                                                          (e! (->PoistaRivi (grid/solun-asia solu/*this* :tunniste-rajapinnan-dataan))))}})]
                                                                                                                                              :luokat #{"salli-ylipiirtaminen"}}
                                                                                                                                             [{:sarakkeet [0 5] :rivit [0 1]}])
                                                                                                                                  {:key (str rivi "-yhteenveto")})
                                                                                                                       (with-meta
                                                                                                                         (grid/taulukko {:nimi ::data-sisalto
                                                                                                                                         :alueet [{:sarakkeet [0 1] :rivit [0 3]}]
                                                                                                                                         :koko konf/auto
                                                                                                                                         :luokat #{"piillotettu" "salli-ylipiirtaminen"}}
                                                                                                                                        (mapv
                                                                                                                                          (fn [index]
                                                                                                                                            (with-meta
                                                                                                                                              (grid/rivi {:koko {:seuraa {:seurattava ::otsikko
                                                                                                                                                                          :sarakkeet :sama
                                                                                                                                                                          :rivit :sama}}
                                                                                                                                                          :osat [(with-meta
                                                                                                                                                                   (solu/tyhja)
                                                                                                                                                                   {:key (str rivi "-" index "-otsikko")})
                                                                                                                                                                 (with-meta
                                                                                                                                                                   (g-pohjat/->SyoteTaytaAlas (gensym "a")
                                                                                                                                                                                              false
                                                                                                                                                                                              (fn [rivit-alla arvo]
                                                                                                                                                                                                (let [grid (grid/root (first rivit-alla))]
                                                                                                                                                                                                  (tayta-alla-olevat-rivit! :aseta-arvo! rivit-alla arvo)
                                                                                                                                                                                                  (paivita-solun-arvo! {:paivitettava-asia :aseta-arvo!
                                                                                                                                                                                                                        :arvo arvo
                                                                                                                                                                                                                        :solu solu/*this*
                                                                                                                                                                                                                        :ajettavat-jarejestykset :deep
                                                                                                                                                                                                                        :triggeroi-seuranta? true})
                                                                                                                                                                                                  (grid/jarjesta-grid-data! grid
                                                                                                                                                                                                                            (keyword (str "data-" rivi)))))
                                                                                                                                                                                              {:on-change (fn [arvo]
                                                                                                                                                                                                            (when arvo
                                                                                                                                                                                                              (paivita-solun-arvo! {:paivitettava-asia :aseta-arvo!
                                                                                                                                                                                                                                    :arvo arvo
                                                                                                                                                                                                                                    :solu solu/*this*
                                                                                                                                                                                                                                    :ajettavat-jarejestykset false})))
                                                                                                                                                                                               :on-focus (fn [_]
                                                                                                                                                                                                           (grid/paivita-osa! solu/*this*
                                                                                                                                                                                                                              (fn [solu]
                                                                                                                                                                                                                                (assoc solu :nappi-nakyvilla? true))))
                                                                                                                                                                                               :on-blur (fn [arvo]
                                                                                                                                                                                                          (when arvo
                                                                                                                                                                                                            (paivita-solun-arvo! {:paivitettava-asia :aseta-arvo!
                                                                                                                                                                                                                                  :arvo arvo
                                                                                                                                                                                                                                  :solu solu/*this*
                                                                                                                                                                                                                                  :ajettavat-jarejestykset :deep
                                                                                                                                                                                                                                  :triggeroi-seuranta? true})))
                                                                                                                                                                                               :on-key-down (fn [event]
                                                                                                                                                                                                              (when (= "Enter" (.. event -key))
                                                                                                                                                                                                                (.. event -target blur)))}
                                                                                                                                                                                              {:on-change [{:positiivinen-numero {:desimaalien-maara 2}}
                                                                                                                                                                                                           {:eventin-arvo {:f poista-tyhjat}}]
                                                                                                                                                                                               :on-blur [:positiivinen-numero
                                                                                                                                                                                                         {:eventin-arvo {:f poista-tyhjat}}]}
                                                                                                                                                                                              {:size 2
                                                                                                                                                                                               :class #{"input-default"}}
                                                                                                                                                                                              summa-formatointi
                                                                                                                                                                                              summa-formatointi-aktiivinen)
                                                                                                                                                                   {:key (str rivi "-" index "-maara")})
                                                                                                                                                                 (with-meta
                                                                                                                                                                   (solu/teksti {:parametrit {:class #{"table-default"}}
                                                                                                                                                                                 :fmt summa-formatointi})
                                                                                                                                                                   {:key (str rivi "-" index "-yhteensa")})
                                                                                                                                                                 (with-meta
                                                                                                                                                                   (solu/teksti {:parametrit {:class #{"table-default"}}})
                                                                                                                                                                   {:key (str rivi "-" index "-indeksikorjattu")})
                                                                                                                                                                 (solu/tyhja)]
                                                                                                                                                          :luokat #{"salli-ylipiirtaminen"}}
                                                                                                                                                         [{:sarakkeet [0 5] :rivit [0 1]}])
                                                                                                                                              {:key (str rivi "-" index)}))
                                                                                                                                          (range 3)))
                                                                                                                         {:key (str rivi "-data-sisalto")})]})
                                                                                                    {:key rivi}))
                                                                                                rivit-ryhmiteltyna))})
                                                   (grid/rivi {:nimi ::yhteenveto
                                                               :koko {:seuraa {:seurattava ::otsikko
                                                                               :sarakkeet :sama
                                                                               :rivit :sama}}
                                                               :osat (conj (vec (repeatedly 2 (fn []
                                                                                                (solu/teksti {:parametrit {:class #{"table-default" "table-default-sum"}}}))))
                                                                           (solu/teksti {:parametrit {:class #{"table-default" "table-default-sum"}}
                                                                                         :fmt summa-formatointi})
                                                                           (solu/teksti {:parametrit {:class #{"table-default" "table-default-sum" "harmaa-teksti"}}
                                                                                         :fmt summa-formatointi})
                                                                           (solu/tyhja #{"table-default" "table-default-sum"}))}
                                                              [{:sarakkeet [0 5] :rivit [0 1]}])]})

                              rajapinta {:otsikot any?
                                         #_#_:yhteensarivit any?
                                         :datarivit any?
                                         :footer any?

                                         :aseta-arvo! any?
                                         :aseta-yhteenveto! any?}
                              yhteensa-data-paivitetty (fn [data]
                                                         (reduce (fn [m {:keys [rivi] :as data-map}]
                                                                   (update m rivi #(merge-with + % (dissoc data-map :rivi))))
                                                                 {}
                                                                 data))]
                          (e! (tuck-apurit/->MuutaTila [:dynaaminen-taulukko-alempi-api :grid] g))
                          (grid/rajapinta-grid-yhdistaminen! g
                                                             rajapinta
                                                             (grid/datan-kasittelija tila
                                                                                     rajapinta
                                                                                     {:otsikot {:polut [[:gridin-otsikot]]
                                                                                                :haku identity}
                                                                                      #_#_:yhteensarivit {:polut [[:data-yhteensa]]
                                                                                                      :haku identity}
                                                                                      :datarivit {:polut [[:data]]
                                                                                                  :luonti-init (fn [tila data]
                                                                                                                 (update tila
                                                                                                                         :data
                                                                                                                         (fn [data]
                                                                                                                           (vec (map-indexed (fn [index m]
                                                                                                                                               (assoc m ::rivitunnistin index))
                                                                                                                                             data)))))
                                                                                                  :haku (fn [data]
                                                                                                          (group-by :rivi data))
                                                                                                  :identiteetti {1 (fn [arvo]
                                                                                                                     (key arvo))}}
                                                                                      :footer {:polut [[:data-yhteensa]]
                                                                                               :haku (fn [data-yhteensa]
                                                                                                       (reduce-kv (fn [m _ {:keys [a b c]}]
                                                                                                                    (-> m
                                                                                                                        (update :a + a)
                                                                                                                        (update :b + b)
                                                                                                                        (update :c + c)))
                                                                                                                  {:rivin-otsikko "Yhteensä"}
                                                                                                                  data-yhteensa))}

                                                                                      :data-yhteenveto {:polut [[:data-yhteensa]]
                                                                                                        :luonti (fn [data-yhteensa]
                                                                                                                  (vec
                                                                                                                    (map (fn [[rivin-otsikko _]]
                                                                                                                           ;; Luonnissa, luotavan nimi on tärkeä, sillä sitä vasten tarkistetaan olemassa olo
                                                                                                                           {(keyword (str "data-yhteenveto-" rivin-otsikko)) ^{:args [rivin-otsikko]} [[:data-yhteensa rivin-otsikko]]})
                                                                                                                         data-yhteensa)))
                                                                                                        :haku (fn [yhteenvetorivin-data yhteenvetorivin-nimi]
                                                                                                                (assoc yhteenvetorivin-data :rivin-otsikko yhteenvetorivin-nimi
                                                                                                                       :poista {:ikoni ikonit/livicon-trash}))}
                                                                                      :data-disable {:polut [[:data]]
                                                                                                     :luonti-init (fn [tila data]
                                                                                                                    (assoc tila :data-disable (reduce (fn [m {rivin-nimi :rivi}]
                                                                                                                                                        (assoc m rivin-nimi false))
                                                                                                                                                      {}
                                                                                                                                                      data)))
                                                                                                     :luonti (fn [data]
                                                                                                               (mapv (fn [{:keys [rivi]}]
                                                                                                                       {(keyword (str "data-disable-" rivi)) ^{:args [rivi]} [[:data-disable]]})
                                                                                                                     data))
                                                                                                     :haku (fn [data-disable rivin-nimi]
                                                                                                             (get data-disable rivin-nimi))}
                                                                                      :data-sisalto {:polut [[:data]]
                                                                                                     :luonti (fn [data]
                                                                                                               (mapv (fn [[rivin-otsikko _]]
                                                                                                                       {(keyword (str "data-" rivin-otsikko)) ^{:args [rivin-otsikko]} [[:data] [:kirjoitettu-data]]})
                                                                                                                     (group-by :rivi data)))
                                                                                                     :haku (fn [data kirjoitettu-data rivin-otsikko]
                                                                                                             (vec
                                                                                                               (keep (fn [{rivi :rivi rivitunnistin ::rivitunnistin :as rivin-data}]
                                                                                                                       (when (= rivi rivin-otsikko)
                                                                                                                         (if (contains? kirjoitettu-data rivitunnistin)
                                                                                                                           (merge rivin-data (get kirjoitettu-data rivitunnistin))
                                                                                                                           rivin-data)))
                                                                                                                     data)))
                                                                                                     :identiteetti {1 (fn [arvo]
                                                                                                                        (::rivitunnistin arvo))
                                                                                                                    2 (fn [arvo]
                                                                                                                        (key arvo))}}}

                                                                                     {:aseta-arvo! (fn [tila arvo {:keys [rivitunnistin arvon-avain]}]
                                                                                                     (let [numeerinen-arvo (try (js/Number (clj-str/replace (or arvo "") "," "."))
                                                                                                                                (catch :default _
                                                                                                                                  arvo))]
                                                                                                       (-> tila
                                                                                                           (update :data (fn [data]
                                                                                                                           (mapv (fn [{tama-rivitunnistin ::rivitunnistin :as rivin-data}]
                                                                                                                                   (if (= tama-rivitunnistin rivitunnistin)
                                                                                                                                     (assoc rivin-data arvon-avain numeerinen-arvo)
                                                                                                                                     rivin-data))
                                                                                                                                 data)))
                                                                                                           (assoc-in [:kirjoitettu-data rivitunnistin arvon-avain] arvo))))}
                                                                                     {:yhteenveto-seuranta {:polut [[:data]]
                                                                                                            :init (fn [tila]
                                                                                                                    (assoc tila :data-yhteensa (yhteensa-data-paivitetty (:data tila))))
                                                                                                            :aseta (fn [tila data]
                                                                                                                     (assoc tila :data-yhteensa (yhteensa-data-paivitetty data)))}
                                                                                      :b-sarakkeen-seuranta {:polut [[:data]]
                                                                                                             :luonti (fn [data]
                                                                                                                       (vec
                                                                                                                         (map-indexed (fn [index _]
                                                                                                                                        ;; Luonnissa, luotavan nimi on tärkeä, sillä sitä vasten tarkistetaan olemassa olo
                                                                                                                                        {(keyword (str "b-sarakkeen-arvo-" index)) ^{:args [index]} [[:data index :a]]})
                                                                                                                                      data)))
                                                                                                             :aseta (fn [tila a index]
                                                                                                                      (assoc-in tila [:data index :b] (* 10 a)))}})
                                                             {[::otsikko] {:rajapinta :otsikot
                                                                           :solun-polun-pituus 1
                                                                           :datan-kasittely identity}
                                                              [::yhteenveto] {:rajapinta :footer
                                                                              :solun-polun-pituus 1
                                                                              :jarjestys [[:rivin-otsikko :a :b :c :poista]]
                                                                              :datan-kasittely (fn [yhteensa]
                                                                                                 (mapv (fn [[_ nimi]]
                                                                                                         nimi)
                                                                                                       yhteensa))}
                                                              [::data] {:rajapinta :datarivit
                                                                        :solun-polun-pituus 0
                                                                        :jarjestys [{:keyfn key
                                                                                     :comp (fn [a b]
                                                                                             (compare a b))}]
                                                                        :datan-kasittely identity
                                                                        :luonti (fn [data-ryhmiteltyna-nimen-perusteella g]
                                                                                  (let [data-avaimet #{:rivi :a :b :c}]
                                                                                    (map-indexed (fn [index [rivin-otsikko _]]
                                                                                                   (merge
                                                                                                     (if (grid/osan-tyyppi? (grid/get-in-grid g [index 0]) impl-grid/Grid)
                                                                                                       (let [rajapinta (keyword (str "data-disable-" rivin-otsikko))
                                                                                                             yhteenveto-grid-rajapinta-asetukset (yhteenveto-grid-rajapinta-asetukset-fn rivin-otsikko)
                                                                                                             aukaistu-yhteeveto-rajapinta-asetukset (aukaistu-yhteenveto-rajapinta-asetukset-fn yhteenveto-grid-rajapinta-asetukset rajapinta)]
                                                                                                         {[:. index 0 ::yhteenveto-auki] (first aukaistu-yhteeveto-rajapinta-asetukset)
                                                                                                          [:. index 0 ::disable-valinta] (second aukaistu-yhteeveto-rajapinta-asetukset)})
                                                                                                       {[:. index ::data-yhteenveto] (yhteenveto-grid-rajapinta-asetukset-fn rivin-otsikko)})
                                                                                                     {[:. index ::data-sisalto] {:rajapinta (keyword (str "data-" rivin-otsikko))
                                                                                                                                 :solun-polun-pituus 2
                                                                                                                                 :jarjestys [{:keyfn :a
                                                                                                                                              :comp (fn [a1 a2]
                                                                                                                                                      (let [muuta-numeroksi (fn [x]
                                                                                                                                                                              (try (js/Number (clj-str/replace (or x "") "," "."))
                                                                                                                                                                                   (catch :default _
                                                                                                                                                                                     x)))]
                                                                                                                                                        (compare (muuta-numeroksi a1) (muuta-numeroksi a2))))}
                                                                                                                                             ^{:nimi :mapit} [:rivi :a :b :c]]
                                                                                                                                 :datan-kasittely (fn [data]
                                                                                                                                                    (mapv (fn [rivi]
                                                                                                                                                            (mapv (fn [[k v]]
                                                                                                                                                                    (when (contains? data-avaimet k)
                                                                                                                                                                      v))
                                                                                                                                                                  rivi))
                                                                                                                                                          data))
                                                                                                                                 :tunnisteen-kasittely (fn [_ data]
                                                                                                                                                         (vec
                                                                                                                                                           (map-indexed (fn [i rivi]
                                                                                                                                                                          (let [rivitunnistin (some #(when (= ::rivitunnistin (first %))
                                                                                                                                                                                                       (second %))
                                                                                                                                                                                                    rivi)]
                                                                                                                                                                            (vec
                                                                                                                                                                              (keep-indexed (fn [j [k _]]
                                                                                                                                                                                              (when-not (= k ::rivitunnistin)
                                                                                                                                                                                                {:rivitunnistin rivitunnistin
                                                                                                                                                                                                 :arvon-avain k
                                                                                                                                                                                                 :osan-paikka [i j]}))
                                                                                                                                                                                            rivi))))
                                                                                                                                                                        data)))}}))
                                                                                                 data-ryhmiteltyna-nimen-perusteella)))}})
                          (grid/grid-tapahtumat g
                                                tila
                                                {:rahavaraukset-disablerivit {:polut [[:data-disable]]
                                                                              :toiminto! (fn [g _ data-disable]
                                                                                           (doseq [rivikontti (-> g (grid/get-in-grid [::data]) (grid/hae-grid :lapset))
                                                                                                   :let [yhteenvedon-ensimmainen-osa (-> rivikontti (grid/get-in-grid [::data-yhteenveto 0]))
                                                                                                         ;; Jos "rivikontti" on avattu, on yhteenvetorivi normi rivin sijasta taulukko, jossa on kaksi riviä.
                                                                                                         ;; Tästä johtuen ensimmäinen osa on rivi. Jos taasen yhteenvetorivi on kiinni, saadaan ensimmäiseksi
                                                                                                         ;; soluksi "Laajenna" solu
                                                                                                         laajenna-osa (if (grid/rivi? yhteenvedon-ensimmainen-osa)
                                                                                                                        (grid/get-in-grid yhteenvedon-ensimmainen-osa [0])
                                                                                                                        yhteenvedon-ensimmainen-osa)
                                                                                                         rivikontin-sisaltaman-datan-nimi (grid/solun-arvo laajenna-osa)
                                                                                                         disable-rivit? (get data-disable rivikontin-sisaltaman-datan-nimi)]]
                                                                                             (solujen-disable! (-> rivikontti (grid/get-in-grid [::data-sisalto]) (grid/hae-grid :lapset))
                                                                                                               disable-rivit?)))}}))))
      (fn []
        (let [{:keys [grid uusi-rivi]} @tila]
          [:div
           ;; Piirretään grid, vasta kun sen määrittäminen on valmis
           (if grid
             [grid/piirra grid]
             [:span "Odotellaan..."])
           [:div {:style {:margin-top "15px" :margin-bottom "5px"}}
            [:label {:for "rivin-nimi"} "Rivin nimi"]
            ;; Tällä voi lisätä uuden datarivin, joka sitten näytetään UI:lla
            [:input#rivin-nimi {:on-change #(e! (->MuokkaaUudenRivinNimea (.. % -target -value)))
                                ;; Uuden rivin voi lisätä painamalla entteriä
                                :on-key-down (fn [event]
                                               (when (= "Enter" (.. event -key))
                                                 (e! (->LisaaRivi))))
                                :value uusi-rivi
                                :style {:display "block"}}]]
           [napit/uusi "Lisää rivi"
            (fn []
              ;; Uuden rivin voi lisätä painamalla nappia
              (e! (->LisaaRivi)))]])))))

(comment
  {::otsikko {:nimi "" :a "A" :b "B" :c "C"}
   :body [{:foo {::data-yhteenveto {}
                 :body [{::data-sisalto [{:a 1 :b 2 :c 3} {:a 1 :b 2 :c 3} {:a 1 :b 2 :c 3}]}]}
           :bar [{}]}]
   ::yhteenveto {}}

  {::otsikko {:nimi "" :a "A" :b "B" :c "C"}
   :body [[{::data-yhteenveto {}
            ::yksiloiva-tieto :foo
            :body [{::data-sisalto [{:a 1 :b 2 :c 3} {:a 1 :b 2 :c 3} {:a 1 :b 2 :c 3}]}]}
           {::data-yhteenveto {}
            ::yksiloiva-tieto :bar
            :body [{::data-sisalto [{:a 1 :b 2 :c 3} {:a 1 :b 2 :c 3} {:a 1 :b 2 :c 3}]}]}]]
   ::yhteenveto {}}

  ;; Staattisella (:rivit) taulukolla
  [{:nimi "" :a "A" :b "B" :c "C"}
   {:foo {::data-yhteenveto {}
          :body [[{:a 1 :b 2 :c 3} {:a 1 :b 2 :c 3} {:a 1 :b 2 :c 3}]]}
    :bar [{}]}
   {}]

  {::otsikko {:nimi "" :a "A" :b "B" :c "C"}
   :body [[{::data-yhteenveto {}
            :body [[{:a 1 :b 2 :c 3} {:a 1 :b 2 :c 3} {:a 1 :b 2 :c 3}]]}
           {::data-yhteenveto {}
            :body [[{:a 1 :b 2 :c 3} {:a 1 :b 2 :c 3} {:a 1 :b 2 :c 3}]]}]]
   ::yhteenveto {}})

(defn dynaaminen-taulukko-ylempi-api []
  (let [tilacursor (r/cursor tila [:dynaaminen-taulukko-ylempi-api])
        testitaulukko (fn [tila-atom dom-id grid-polku data-polku]
                        (let [syote-tayta-alas-predef (taulukko/predef :syote-tayta-alas nil)
                              input-predef (taulukko/predef :input nil)
                              numero-predef (taulukko/predef :numero nil)
                              laajenna-predef (taulukko/predef :laajenna nil)
                              yhteenvetorivi (fn [f]
                                               (vec
                                                 (concat [(solu/laajenna (merge laajenna-predef
                                                                                {:parametrit {:class #{"table-default" "lihavoitu"}}}))]
                                                         (map (fn [sarake]
                                                                {:solu (solu/teksti {:parametrit {:class #{"table-default"}}
                                                                                     :fmt (get numero-predef :fmt)})
                                                                 :riippuu-toisesta {:riippuvuudet [[:/ ::data ::data-sisalto ^:sarake sarake]]
                                                                                    :kasittely-fn (fn [sarakkeen-arvot]
                                                                                                    (reduce + 0 sarakkeen-arvot))}})
                                                              [:a :b :c])
                                                         [(solu/ikoni {:nimi "poista"
                                                                       :toiminnot {:on-click (fn [_]
                                                                                               (e! (->PoistaRivi (grid/solun-asia solu/*this* :tunniste-rajapinnan-dataan))))}})])))
                              taulukkomaaritelma {:conf {:ratom tila-atom
                                                         :dom-id dom-id
                                                         :grid-polku grid-polku
                                                         :data-polku data-polku}
                                                  :vaihto-osat {:yhteenveto-checkboxilla {:body [{:conf {:jarjestys [:rivi :a :b :c :poista]}
                                                                                                  :osat (yhteenvetorivi (fn [this _]
                                                                                                                          (taulukko/vaihda-osa-takaisin! this (grid/osa-polusta this [:.. :..]))))}
                                                                                                 {:conf {:jarjestys [:rivi :a :b :c :poista]}
                                                                                                  :osat (vec
                                                                                                          (cons (vayla-checkbox (fn [this event]
                                                                                                                                  (.preventDefault event)
                                                                                                                                  (let [disable-rivit? (not (grid/solun-arvo this))]
                                                                                                                                    (e! (tuck-apurit/->MuutaTila [:data-disable] disable-rivit?))))
                                                                                                                                "Disabloi rivit")
                                                                                                                (repeatedly 4 (fn [] (solu/tyhja)))))}]}}
                                                  :datavaikutukset-taulukkoon {:rahavaraukset-disablerivit {:polut [[:data-disable]]
                                                                                                            :toiminto! (fn [g _ data-disable]
                                                                                                                         (doseq [rivikontti (-> g (grid/get-in-grid [::data]) (grid/hae-grid :lapset))
                                                                                                                                 :let [yhteenvedon-ensimmainen-osa (-> rivikontti (grid/get-in-grid [::data-yhteenveto 0]))
                                                                                                                                       laajenna-osa (if (grid/rivi? yhteenvedon-ensimmainen-osa)
                                                                                                                                                      (grid/get-in-grid yhteenvedon-ensimmainen-osa [0])
                                                                                                                                                      yhteenvedon-ensimmainen-osa)
                                                                                                                                       rivikontin-sisaltaman-datan-nimi (grid/solun-arvo laajenna-osa)
                                                                                                                                       disable-rivit? (get data-disable rivikontin-sisaltaman-datan-nimi)]]
                                                                                                                           (solujen-disable! (-> rivikontti (grid/get-in-grid [::data-sisalto]) (grid/hae-grid :lapset))
                                                                                                                                             disable-rivit?)))}}
                                                  :taulukko {:header {:conf {:nimi ::otsikko
                                                                             :koko (-> konf/livi-oletuskoko
                                                                                       (assoc-in [:sarake :leveydet] {0 "9fr"
                                                                                                                      4 "1fr"})
                                                                                       (assoc-in [:sarake :oletus-leveys] "3fr"))
                                                                             :luokat #{"salli-ylipiirtaminen"}
                                                                             :jarjestys [:rivi :a :b :c :poista]}
                                                                      :osat (conj (mapv (fn [nimi]
                                                                                          (solu/otsikko {:jarjesta-fn! jarjesta-fn!
                                                                                                         :parametrit {:class #{"table-default" "table-default-header"}}
                                                                                                         :nimi nimi}))
                                                                                        [:rivi :a :b :c])
                                                                                  (solu/tyhja #{"table-default" "table-default-header"}))}
                                                             :body [{:conf {:nimi ::data
                                                                            :yksiloivakentta :rivin-nimi}
                                                                     :toistettava-osa {:conf {:sarakkeiden-nimet [:rivin-nimi :a :b :c :poista]}
                                                                                       :header {:conf {:nimi ::data-yhteenveto
                                                                                                       :vaihdettava-osa :yhteenveto-checkboxilla
                                                                                                       :jarjestys [:rivi :a :b :c :poista]}
                                                                                                :osat (yhteenvetorivi (fn [this auki?]
                                                                                                                        (taulukko/vaihda-osa! this (grid/osien-yhteinen-asia (grid/vanhempi this) :nimi-polku))))}
                                                                                       :body [{:conf {:nimi ::data-sisalto
                                                                                                      :luokat #{"piillotettu" "salli-ylipiirtaminen"}}
                                                                                               :body (mapv (fn [index]
                                                                                                             {:conf {:jarjestys [:rivi :a :b :c :poista]}
                                                                                                              :osat [(solu/tyhja)
                                                                                                                     (grid/aseta-nimi (g-pohjat/syote-tayta-alas false
                                                                                                                                                                 (get syote-tayta-alas-predef :nappia-painettu!)
                                                                                                                                                                 (merge (select-keys syote-tayta-alas-predef #{:on-focus})
                                                                                                                                                                        (get input-predef :toiminnot))
                                                                                                                                                                 (get numero-predef :kayttaytymiset)
                                                                                                                                                                 (get input-predef :parametrit)
                                                                                                                                                                 (get numero-predef :fmt)
                                                                                                                                                                 (get numero-predef :fmt-aktiivinen))
                                                                                                                                      :a-solu)
                                                                                                                     {:solu (solu/teksti {:parametrit {:class #{"table-default"}}
                                                                                                                                          :fmt (get numero-predef :fmt)})
                                                                                                                      :riippuu-toisesta {:riippuvuudet [[:a-kentta]]
                                                                                                                                         :kasittely-fn (fn [a-arvo]
                                                                                                                                                         (* 10 a-arvo))}}
                                                                                                                     (solu/teksti {:parametrit {:class #{"table-default"}}
                                                                                                                                   :fmt (get numero-predef :fmt)})
                                                                                                                     (solu/tyhja)]})
                                                                                                           (range 3))}]}}]
                                                             :footer {:conf {:nimi ::yhteenveto
                                                                             :jarjestys [:rivi :a :b :c :poista]}
                                                                      :osat (vec
                                                                              (concat [(solu/tyhja #{"table-default" "table-default-sum"})]
                                                                                      (map (fn [sarake]
                                                                                             {:solu (solu/teksti {:parametrit {:class #{"table-default"}}
                                                                                                                  :fmt (get numero-predef :fmt)})
                                                                                              :riippuu-toisesta {:riippuvuudet [[:/ ::data ^:sarake sarake]]
                                                                                                                 :kasittely-fn (fn [sarakkeen-arvot]
                                                                                                                                 (reduce + 0 sarakkeen-arvot))}})
                                                                                           [:a :b :c])
                                                                                      [(solu/tyhja #{"table-default" "table-default-sum"})]))}}}]
                          (taulukko/tee-taulukko! taulukkomaaritelma)))]
    (komp/luo
      (komp/piirretty (fn [_]
                        (testitaulukko tila
                                   "dynaaminen-taulukko-ylempi-api"
                                   [:dynaaminen-taulukko-ylempi-api :grid]
                                   [:dynaaminen-taulukko-ylempi-api :data])))
      (fn []
        (let [{:keys [grid]} @tilacursor]
          (if grid
            [grid/piirra grid]
            [:span "Odotellaan dynaaminen-taulukko-ylempi-api..."]))))))


(defn pelilauta [_]
  ;; Luodaan lennossa muutama CSS luokka, jota käytetään tässä esimerkissä
  (luo-css-luokat! {"musta" "{ background-color: black; }"
                    "valkoinen" "{ background-color: white; }"
                    "vihrea-merkki" "{ color: green; }"})
  ;; Yhden solun korkeus
  (let [korkeus-px 40
        ;; Luodaan uusi grid. Tarkoituksena olisi luoda 8x8 kertaa lauta, joka reagoin hiiren liikkeisiin

        ;; Annetaan nimi, jota voidaan käyttää apuna gridiä navigoitaessa
        g (grid/grid {:nimi ::lauta
                      ;; Dom id, jota voidaan käyttää apuna, jos on tarvetta käpistellä itse DOM:ia
                      :dom-id "pelilauta"
                      ;; root-fn kutsutaan kun halutaan löytää määrritelty gridi RAtomista. 
                      :root-fn (fn [] (get-in @tila [:pelilauta :grid]))
                      ;; paivita-root! kutsutaan kun halutaan päivittää määriteltyä gridiä RAtomissa
                      :paivita-root! (fn [f]
                                       (swap! tila
                                              (fn [tila]
                                                (update-in tila [:pelilauta :grid] f))))
                      ;; alueet sisältää tiedon siitä, että miten osat on organisoitu gridiin. Näitä voisi olla useampikin,
                      ;; mutta käytännössä aina yksi, kun määritetään normi taulukko. Eli vektorin sisälle annetaan yksi
                      ;; map, joka sisältää :sarakkeet ja :rivit avaimet. Näiden avulla kerrotaan, että minne päin gridiä
                      ;; osat pitäisi luoda. Tässä tapauksessa luodaan 8x8 lauta.
                      :alueet [{:sarakkeet [0 8] :rivit [0 8]}]
                      ;; koko kertoo minkä kokoisia neliskulmia gridin ruudukoista pitäisi tehdä. Tähän voi määrittää tarkalleen
                      ;; rivi ja sarake parit, mutta tässä esimerkissä käytetään oletus-leveys ja oletus-korkeus arvoja, jotka
                      ;; annetaan kaikille gridin osille.
                      :koko (-> konf/auto
                                (assoc-in [:sarake :oletus-leveys] (str korkeus-px "px"))
                                (assoc-in [:rivi :oletus-korkeus] (str korkeus-px "px")))
                      ;; Osat osio sisältää vektorissa kaikki ne osat, jotka tulisi piirtää gridin sisälle. Jokaisen
                      ;; osan tulee toteuttaa harja.ui.taulukko.protokollat.grid-osa/IGridOsa protokolla
                      :osat (vec
                              ;; Tehdään muutama loop, jotta saadaan CSS-luokat määritettyä oikein jokaiselle solulle
                              (mapcat (fn [rivi-index]
                                        (let [aloitus-n (if (odd? rivi-index) 1 0)]
                                          (map (fn [sarake-index]
                                                 ;; Määritetään jokainen solu solu/Ikoni tyyppiseksi. Annetaan argumentteina
                                                 ;; CSS-luokat ja on-mouse-over callback funktio, joka asettaa juuri
                                                 ;; hiirellä hoveroidun paikkatiedon RAtomiin
                                                 (solu/ikoni {:toiminnot {:on-mouse-over (fn [_]
                                                                                           (swap! tila assoc-in [:pelilauta :paikka] [rivi-index (- sarake-index aloitus-n)]))}
                                                              :parametrit {:class (conj #{"vihrea-merkki"}
                                                                                        (if (odd? sarake-index)
                                                                                          "musta"
                                                                                          "valkoinen"))}}))
                                               (range aloitus-n (+ aloitus-n 8)))))
                                      (range 0 8)))})
        ;; Luodaan taulukolle simppeli rajapinta. Rajapinta on map, jonka avaimina toimii rajapinnan nimi ja arvona
        ;; spec, jota vasten arvot validoidaan.
        rajapinta {:ikonin-paikka any?}]
    ;; Tallennetaan gridin RAtomiin. Tallennetaan sinne minne ollaan taulukon määrityksessä kerrottukin sen paikka.
    (swap! tila assoc-in [:pelilauta :grid] g)
    ;; Yhdistetään grid rajapinnan kautta RAtomiin, josta se saa datansa.
    (grid/rajapinta-grid-yhdistaminen! g
                                       rajapinta
                                       ;; datan-kasittelija hoitaa RAtomista tulevan datan munklaamisen rajapinnassa
                                       ;; määritettyyn muotoon gridille sekä gridiltä tulevien datamuutosten (tääsä ei käytetä) munklaamisen
                                       ;; RAtomiin. Se hoitaa myös triggerit, joita tässä ei käytetä
                                       (grid/datan-kasittelija tila
                                                               rajapinta
                                                               ;; Gridin lukurajapinnat.

                                                               ;; polut on vektori polkuja RAtomiin. Tässä ollaan kiinnostuttu ainoastaan
                                                               ;; pelilaudan paikasta, joten vain yksi polku on määritetty
                                                               {:ikonin-paikka {:polut [[:pelilauta :paikka]]
                                                                                ;; haku on funktio, jonka avulla voidaan muokata RAtomista
                                                                                ;; tulevaa dataa gridin haluamaan muotoon. Se saa argumeteikseen
                                                                                ;; polkujen datan. Eli kun nyt on yksi polku, se saa yhden
                                                                                ;; argumentin. Paikkatiedolle ei haluta tehdä mitään erityistä,
                                                                                ;; joten identity on ihan ok funktio.
                                                                                :haku identity}}
                                                               ;; Gridin kirjoitusrajapinnat. Ei tarvita niitä tässä.
                                                               {}
                                                               ;; Triggerit. Ei tarvita niitä tässä
                                                               {})
                                       ;; Tässä määritetänä sitten gridin pääty rajapinnasta. Eli kun datan-kasittelija
                                       ;; on hankkinu datan RAtomista, pitää se data vilä viedä gridin soluille. Tämä
                                       ;; on mappi, jossa avaimena on polku gridin osaan ja arvona on määritelmä sille,
                                       ;; että mitä rajapinnasta tulevalle datalle pitäisi tehdä.

                                       ;; Tässä esimerkissä on suhkot simppeli gridpuu, jossa on vain root käytössä.
                                       ;; Eli polkuna on tyhjä vektori. :rajapinta määrittää sen, että mitä rajapintaa
                                       ;; halutaan kuunnella.
                                       {[] {:rajapinta :ikonin-paikka
                                            ;; Yhtä rajapintaa voi kuunnella gridpuun osa, joka itsessään on jo melko
                                            ;; syvä. Tässä on kumminkin vain root, jolla on lapsina soluja, joten
                                            ;; syvyydeksi määritetään 1.
                                            :solun-polun-pituus 1
                                            ;; datan-kasittely palauttaa vektorin, jossa yski vektorin osan sisältämä
                                            ;; data annetaan yhdelle gridin solulle. Eli meillä on 8x8 grid (64 solua),
                                            ;; joten jos halutaan antaa kaikille soluille jotain dataa, pitäisi tämän
                                            ;; palauttaa 64 pituinen vektori. Halutaan kummiskin näyttää ikoni vain yhdessä
                                            ;; solussa, joten tollaiseen vektoriin ei ole tarvesta.
                                            :datan-kasittely (fn [data-rajapinnasta]
                                                               (when data-rajapinnasta
                                                                 (println "data-rajapinnasta (eli paikka) " data-rajapinnasta)
                                                                 (let [[rivi sarake] data-rajapinnasta]
                                                                   ;; Koska data annetaan järjestyksessä, pitää laskea, että
                                                                   ;; kuinka iso vektori tarvitaan. Jos saadaan paikaksi vaikapa
                                                                   ;; [4 1] tarkoittaa se, että halutaan näyttää ikoni viidennen rivin
                                                                   ;; toisessa sarakkessa. Eli tarvitaan 25 tyhjää ja sen jälkeen
                                                                   ;; ikoni solun ymmärtämä data.
                                                                   (conj (vec (repeat (+ (* rivi 8) sarake) nil))
                                                                         {:ikoni ikonit/check}))))}})
    (fn [{:keys [grid]}]
      (if grid
        [:div {:style {:max-width (str (* 8 korkeus-px) "px")}}
         [grid/piirra grid]]
        [:span "Odotellaan pelilautaa..."]))))

(defn padding [& args]
  [:div {:style {:padding-bottom "10px"}}
   (vec args)])

(defn esimerkit* [e*! app]
  ;; Asetetaan tämän nimiavaruuden e! arvoksi e*!, jotta tuota tuckin muutosfunktiota ei tarvitse passata jokaiselle komponentille
  (set! e! e*!)
  [:div
   [padding pelilauta (:pelilauta app)]
   [padding dynaaminen-taulukko-ylempi-api]
   [padding dynaaminen-taulukko-alempi-api]])

(defn foo []
  [:div
   [tuck/tuck tila esimerkit*]])
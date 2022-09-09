(ns harja.views.urakka.suunnittelu.kustannussuunnitelma.johto-ja-hallintokorvaus-osio
  (:require [reagent.core :as r]
            [harja.tiedot.urakka.suunnittelu.mhu-kustannussuunnitelma :as t]
            [harja.ui.taulukko.grid :as grid]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.kentat :as kentat]
            [harja.tyokalut.yleiset :as tyokalut]
            [harja.views.urakka.suunnittelu.kustannussuunnitelma.yhteiset :as ks-yhteiset :refer [e!]]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.ui.taulukko.impl.solu :as solu]
            [harja.ui.taulukko.grid-pohjat :as g-pohjat]
            [harja.fmt :as fmt]
            [clojure.string :as clj-str]
            [harja.ui.modal :as modal]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.pvm :as pvm]
            [harja.ui.grid :as vanha-grid]
            [harja.ui.taulukko.grid-osan-vaihtaminen :as gov]
            [harja.views.urakka.suunnittelu.kustannussuunnitelma.grid-apurit :as grid-apurit]
            [harja.domain.mhu :as mhu]))


;; -- Johto- ja hallintokorvaus osioon liittyvät gridit --

(def toimistokulut-grid
  (partial grid-apurit/maarataulukko-grid "toimistokulut" [:yhteenvedot :johto-ja-hallintokorvaukset]
    {:tallennus-onnistui-event t/->TallennaToimistokulutOnnistui}))

(defn- yhteenveto-grid-rajapinta-asetukset
  [toimenkuva maksukausi data-koskee-ennen-urakkaa?]
  (let [yksiloiva-nimen-paate (str "-" toimenkuva "-" maksukausi)
        kuvaus (vec (concat [:toimenkuva :tunnit :tuntipalkka :yhteensa] (when-not (t/post-2022?) [:kk-v])))]
    {:rajapinta (keyword (str "yhteenveto" yksiloiva-nimen-paate))
     :solun-polun-pituus 1
     :jarjestys [(with-meta kuvaus {:nimi :mapit})]
     :datan-kasittely (fn [yhteenveto]
                        (mapv (fn [[_ v]]
                                v)
                          yhteenveto))
     :tunnisteen-kasittely (fn [osat data]
                             (second
                               (reduce (fn [[loytynyt? tunnisteet] osa]
                                         (let [syote-osa? (instance? solu/Syote osa)
                                               osa (when syote-osa?
                                                     (if loytynyt?
                                                       :tuntipalkka
                                                       :tunnit))
                                               ;; Note: Nämä tiedot valuvat jotenkin :aseta-jh-yhteenveto! toiminnon kautta
                                               ;;       jh-yhteenvetopaivitys -funktiolle parametreiksi.
                                               tunniste {:osa osa :toimenkuva toimenkuva :maksukausi maksukausi
                                                         :data-koskee-ennen-urakkaa? data-koskee-ennen-urakkaa?}]
                                           [(or loytynyt? syote-osa?)
                                            (conj tunnisteet tunniste)]))
                                 [false []]
                                 (grid/hae-grid osat :lapset))))}))

(defn- muokkausrivien-rajapinta-asetukset
  [nimi]
  {:rajapinta (keyword (str "yhteenveto-" nimi))
   :solun-polun-pituus 1
   :jarjestys [^{:nimi :mapit} [:toimenkuva :tunnit :tuntipalkka :yhteensa :maksukausi]]
   :datan-kasittely (fn [yhteenveto]
                      (mapv (fn [[_ v]]
                              v)
                        yhteenveto))
   :tunnisteen-kasittely (fn [_ _]
                           (mapv (fn [index]
                                   (assoc
                                     (case index
                                       0 {:osa :toimenkuva}
                                       1 {:osa :tunnit}
                                       2 {:osa :tuntipalkka}
                                       3 {:osa :yhteensa}
                                       4 {:osa :maksukausi})
                                     :omanimi nimi))
                             (range 5)))})

(defn- blur-tallenna!
  ([tallenna-kaikki? etsittava-osa solu]
   (blur-tallenna! tallenna-kaikki? etsittava-osa solu nil))
  ([tallenna-kaikki? etsittava-osa solu muutos]
   (if tallenna-kaikki?
     (e! (t/->TallennaJohtoJaHallintokorvaukset
           (mapv #(grid/solun-asia (get (grid/hae-grid % :lapset) 1)
                    :tunniste-rajapinnan-dataan)
             (grid/hae-grid
               (get (grid/hae-grid (grid/etsi-osa (grid/root solu) etsittava-osa)
                      :lapset)
                 1)
               :lapset))))
     (e! (t/->TallennaJohtoJaHallintokorvaukset
           [(grid/solun-asia solu :tunniste-rajapinnan-dataan)])))))

(defn- nappia-painettu-tallenna! 
  ([rivit-alla]
   (nappia-painettu-tallenna! rivit-alla nil))
  ([rivit-alla muutos]
   (e! (t/->TallennaJohtoJaHallintokorvaukset
         (vec (keep (fn [rivi]
                      (let [haettu-solu (grid/get-in-grid rivi [1])
                            piilotettu? (grid/piilotettu? rivi)]
                        (when-not piilotettu?
                          (grid/solun-asia haettu-solu :tunniste-rajapinnan-dataan))))
                rivit-alla))))))

(defn- rividisable!
  [g index kuukausitasolla?]
  (ks-yhteiset/maara-solujen-disable! (grid/get-in-grid g [::g-pohjat/data index ::t/data-sisalto])
    (not kuukausitasolla?))
  (ks-yhteiset/maara-solujen-disable! (if-let [osa (grid/get-in-grid g [::g-pohjat/data index ::t/data-yhteenveto 0 1])]
                                        osa
                                        (grid/get-in-grid g [::g-pohjat/data index ::t/data-yhteenveto 1]))
    kuukausitasolla?))

(defn- disable-osa-indexissa!
  [rivi indexit disabled?]
  (grid/paivita-grid! rivi
    :lapset
    (fn [osat]
      (vec (map-indexed
             (fn [index solu]
               (if (contains? indexit index)
                 (if (grid/pudotusvalikko? solu)
                   (assoc-in solu [:livi-pudotusvalikko-asetukset :disabled?] disabled?)
                   (assoc-in solu [:parametrit :disabled?] disabled?))
                 solu))
             osat)))))

(defn johto-ja-hallintokorvaus-laskulla-grid []
  (let [taulukon-id "johto-ja-hallintokorvaus-laskulla-taulukko"               
        vakiorivit (mapv (fn [{:keys [toimenkuva maksukausi hoitokaudet] :as toimenkuva-kuvaus}]
                           (let [yksiloiva-nimen-paate (str "-" toimenkuva "-" maksukausi)]
                             (if (t/toimenpide-koskee-ennen-urakkaa? hoitokaudet)
                               {:tyyppi :rivi
                                :nimi ::t/data-yhteenveto
                                :osat [{:tyyppi :teksti
                                        :luokat #{"table-default"}}
                                       (ks-yhteiset/syote-solu
                                         {:nappi? false :fmt ks-yhteiset/yhteenveto-format :paivitettava-asia :aseta-jh-yhteenveto!
                                          :blur-tallenna! (partial blur-tallenna! false nil)})
                                       (ks-yhteiset/syote-solu
                                         {:nappi? false :fmt ks-yhteiset/yhteenveto-format :paivitettava-asia :aseta-jh-yhteenveto!
                                          :blur-tallenna! (partial blur-tallenna! false nil)})
                                       {:tyyppi :teksti
                                        :luokat #{"table-default"}
                                        :fmt ks-yhteiset/yhteenveto-format}
                                       {:tyyppi :teksti
                                        :luokat #{"table-default"}
                                        :fmt (fn [teksti]
                                               (if (nil? teksti)
                                                 ""
                                                 (let [sisaltaa-erottimen? (boolean (re-find #",|\." (str teksti)))]
                                                   (if sisaltaa-erottimen?
                                                     (fmt/desimaaliluku (clj-str/replace (str teksti) "," ".") 1 true)
                                                     teksti))))}]}
                               {:tyyppi :taulukko
                                :nimi (str toimenkuva "-" maksukausi "-taulukko")
                                :osat [{:tyyppi :rivi
                                        :nimi ::t/data-yhteenveto
                                        :osat [{:tyyppi :laajenna
                                                :aukaise-fn
                                                (fn [this auki?]
                                                  (if auki?
                                                    (ks-yhteiset/rivi-kuukausifiltterilla! this
                                                      true
                                                      (keyword (str "kuukausitasolla?" yksiloiva-nimen-paate))
                                                      [:gridit :johto-ja-hallintokorvaukset :kuukausitasolla? toimenkuva maksukausi]
                                                      [:. ::t/yhteenveto] (yhteenveto-grid-rajapinta-asetukset toimenkuva maksukausi false)
                                                      [:. ::t/valinta] {:rajapinta (keyword (str "kuukausitasolla?" yksiloiva-nimen-paate))
                                                                        :solun-polun-pituus 1
                                                                        :datan-kasittely (fn [kuukausitasolla?]
                                                                                           [kuukausitasolla? nil nil nil])})
                                                    (do
                                                      (ks-yhteiset/rivi-ilman-kuukausifiltteria! this
                                                        [:.. ::t/data-yhteenveto] (yhteenveto-grid-rajapinta-asetukset toimenkuva maksukausi false))
                                                      (e! (tuck-apurit/->MuutaTila
                                                            [:gridit :johto-ja-hallintokorvaukset :kuukausitasolla? toimenkuva maksukausi] false))))
                                                  (t/laajenna-solua-klikattu this auki? taulukon-id [::g-pohjat/data] {:sulkemis-polku [:.. :.. :.. 1]}))
                                                :auki-alussa? false
                                                :luokat #{"table-default"}}
                                               (ks-yhteiset/syote-solu
                                                 {:nappi? false :fmt ks-yhteiset/yhteenveto-format :paivitettava-asia :aseta-jh-yhteenveto!
                                                  :blur-tallenna! (partial blur-tallenna! true (str toimenkuva "-" maksukausi "-taulukko"))})
                                               (ks-yhteiset/syote-solu
                                                 {:nappi? false :fmt ks-yhteiset/yhteenveto-format :paivitettava-asia :aseta-jh-yhteenveto!
                                                  :blur-tallenna! (partial blur-tallenna! true (str toimenkuva "-" maksukausi "-taulukko"))})
                                               {:tyyppi :teksti
                                                :luokat #{"table-default"}
                                                :fmt ks-yhteiset/yhteenveto-format}
                                               {:tyyppi :teksti
                                                :luokat #{"table-default"}
                                                :fmt (fn [teksti]
                                                       (if (nil? teksti)
                                                         ""
                                                         (let [sisaltaa-erottimen? (boolean (re-find #",|\." (str teksti)))]
                                                           (if sisaltaa-erottimen?
                                                             (fmt/desimaaliluku (clj-str/replace (str teksti) "," ".") 1 true)
                                                             teksti))))}]}
                                       {:tyyppi :taulukko
                                        :nimi ::t/data-sisalto
                                        :luokat #{"piilotettu"}
                                        :osat (vec (repeatedly (t/kk-v-toimenkuvan-kuvaukselle toimenkuva-kuvaus)
                                                     (fn []
                                                       {:tyyppi :rivi
                                                        :osat [{:tyyppi :teksti
                                                                :luokat #{"table-default"}
                                                                :fmt ks-yhteiset/aika-tekstilla-fmt}
                                                               (ks-yhteiset/syote-solu {:nappi? true :fmt ks-yhteiset/summa-formatointi-uusi :paivitettava-asia :aseta-tunnit!
                                                                                        :solun-index 1
                                                                                        :blur-tallenna! (partial blur-tallenna! false (str toimenkuva "-" maksukausi "-taulukko"))
                                                                                        :nappia-painettu-tallenna! nappia-painettu-tallenna!})
                                                               {:tyyppi :tyhja}
                                                               {:tyyppi :tyhja}
                                                               {:tyyppi :tyhja}]})))}]})))
                     (t/pohjadatan-versio))

        muokattavat-rivit (mapv (fn [index]
                                  (let [rivin-nimi (t/jh-omienrivien-nimi index)]
                                    ;; Avautuva itse lisätty rivi (sisältää alitaulukon)
                                    {:tyyppi :taulukko
                                     :nimi rivin-nimi
                                     :osat [{:tyyppi :rivi
                                             :nimi ::t/data-yhteenveto
                                             ;; :oma = itselisätyn toimenkuvan nimi-solu
                                             :osat [{:tyyppi :oma
                                                     :constructor
                                                     (fn [_]
                                                       (ks-yhteiset/laajenna-syote
                                                         (fn [this auki?]
                                                           (if auki?
                                                             (ks-yhteiset/rivi-kuukausifiltterilla! this
                                                               true
                                                               (keyword (str "kuukausitasolla?-" rivin-nimi))
                                                               [:gridit :johto-ja-hallintokorvaukset :kuukausitasolla? rivin-nimi]
                                                               [:. ::t/yhteenveto]
                                                               (muokkausrivien-rajapinta-asetukset rivin-nimi)

                                                               [:. ::t/valinta]
                                                               {:rajapinta (keyword (str "kuukausitasolla?-" rivin-nimi))
                                                                :solun-polun-pituus 1
                                                                :datan-kasittely (fn [kuukausitasolla?]
                                                                                   [kuukausitasolla? nil nil nil])})
                                                             (do
                                                               (ks-yhteiset/rivi-ilman-kuukausifiltteria! this
                                                                 [:.. ::t/data-yhteenveto]
                                                                 (muokkausrivien-rajapinta-asetukset rivin-nimi))
                                                               (e! (tuck-apurit/->MuutaTila
                                                                     [:gridit :johto-ja-hallintokorvaukset :kuukausitasolla? rivin-nimi] false))))
                                                           (t/laajenna-solua-klikattu this auki? taulukon-id
                                                             [::g-pohjat/data] {:sulkemis-polku [:.. :.. :.. 1]}))
                                                         false
                                                         {:on-change
                                                          (fn [arvo]
                                                            (t/paivita-solun-arvo {:paivitettava-asia :aseta-jh-yhteenveto!
                                                                                   :arvo arvo
                                                                                   :solu solu/*this*
                                                                                   :ajettavat-jarjestykset #{:mapit}}
                                                              false))
                                                          :on-blur
                                                          (fn [arvo]
                                                            (let [arvo (if (= "" (clj-str/trim arvo))
                                                                         nil
                                                                         arvo)
                                                                  solu solu/*this*
                                                                  paivita-ui! (fn []
                                                                                (t/paivita-solun-arvo {:paivitettava-asia :aseta-jh-yhteenveto!
                                                                                                       :arvo arvo
                                                                                                       :solu solu
                                                                                                       :ajettavat-jarjestykset true
                                                                                                       :triggeroi-seuranta? true}
                                                                                  true))
                                                                  paivita-kanta! (fn [] (e! (t/->TallennaToimenkuva rivin-nimi)))
                                                                  paivita! (fn []
                                                                             (paivita-ui!)
                                                                             (paivita-kanta!))
                                                                  peruuta! (fn [vanha-arvo]
                                                                             (e! (tuck-apurit/->MuutaTila
                                                                                   [:gridit :johto-ja-hallintokorvaukset :yhteenveto rivin-nimi :toimenkuva]
                                                                                   vanha-arvo)))]

                                                              ;; Jos custom toimenkuvan nimi pyyhitään, niin täytyy triggeröidä
                                                              ;; toimenkuvaan liittyvän datan poisto tietokannasta.
                                                              (if (nil? arvo)
                                                                (let [paivita-seuraavalla-tickilla! (fn []
                                                                                                      (r/next-tick paivita!))]
                                                                  (e! (t/->PoistaOmaJHDdata :toimenkuva
                                                                        rivin-nimi
                                                                        :molemmat ;; Poistetaan data kaikilta maksukausilta
                                                                        modal/piilota!
                                                                        paivita-seuraavalla-tickilla!
                                                                        (fn [toimenkuva data-hoitokausittain poista! vanhat-arvot]
                                                                          (ks-yhteiset/poista-modal! :toimenkuva
                                                                            data-hoitokausittain
                                                                            poista!
                                                                            {:toimenkuva toimenkuva}
                                                                            (partial peruuta!
                                                                              (some :toimenkuva vanhat-arvot)))))))
                                                                (paivita!))))
                                                          :on-key-down (fn [event]
                                                                         (when (= "Enter" (.. event -key))
                                                                           (.. event -target blur)))}
                                                         {:on-change [:eventin-arvo]
                                                          :on-blur [:eventin-arvo]}
                                                         {:class #{"input-default"}
                                                          :placeholder "Kirjoita muu toimenkuva"}
                                                         identity
                                                         identity))
                                                     :auki-alussa? false
                                                     :luokat #{"table-default"}}
                                                    ;; Tunnit-solu
                                                    (ks-yhteiset/syote-solu
                                                      {:nappi? false :fmt ks-yhteiset/yhteenveto-format :paivitettava-asia :aseta-jh-yhteenveto!
                                                       :blur-tallenna! (partial blur-tallenna! true rivin-nimi)
                                                       :nappia-painettu-tallenna! nappia-painettu-tallenna!})
                                                    ;; Tuntipalkka-solu
                                                    (ks-yhteiset/syote-solu
                                                      {:nappi? false :fmt ks-yhteiset/yhteenveto-format :paivitettava-asia :aseta-jh-yhteenveto!
                                                       :blur-tallenna! (partial blur-tallenna! true rivin-nimi)
                                                       :nappia-painettu-tallenna! nappia-painettu-tallenna!})
                                                    ;; Yhteenveto-solu (Yhteensä/kk)
                                                    {:tyyppi :teksti
                                                     :luokat #{"table-default"}
                                                     :fmt ks-yhteiset/yhteenveto-format}
                                                    ;; Pudotusvalikko-solu
                                                    {:tyyppi :pudotusvalikko
                                                     :data-cy "kk-v-valinnat"
                                                     :valitse-fn
                                                     (fn [maksukausi]
                                                       (let [solu solu/*this*
                                                             paivita-ui! (fn []
                                                                           (t/paivita-solun-arvo {:paivitettava-asia :aseta-jh-yhteenveto!
                                                                                                  :arvo maksukausi
                                                                                                  :solu solu
                                                                                                  :ajettavat-jarjestykset true
                                                                                                  :triggeroi-seuranta? true}
                                                                             false))]

                                                         ;; NOTE: Tallentamisen yhteydessä pitää olla tarkkana, että maksukausi on varmasti
                                                         ;;       päivitetty soluun ja että päivitetty solu on käytössä tallenna-funktiossa.
                                                         (if (= :molemmat maksukausi)
                                                           (do
                                                             (paivita-ui!)
                                                             (blur-tallenna! true rivin-nimi solu))

                                                           (e! (t/->PoistaOmaJHDdata :maksukausi
                                                                 rivin-nimi
                                                                 maksukausi
                                                                 modal/piilota!
                                                                 #(r/next-tick paivita-ui!)
                                                                 (fn [toimenkuva data-hoitokausittain poista! _]
                                                                   (ks-yhteiset/poista-modal! :toimenkuva
                                                                     data-hoitokausittain
                                                                     poista!
                                                                     {:toimenkuva toimenkuva})))))))
                                                     :format-fn (fn [teksti]
                                                                  (str (mhu/maksukausi->kuukausien-lkm teksti)))
                                                     :rivin-haku (fn [pudotusvalikko]
                                                                   (grid/osa-polusta pudotusvalikko [:.. :..]))
                                                     :vayla-tyyli? true
                                                     :vaihtoehdot t/kk-v-valinnat}]}
                                            {:tyyppi :taulukko
                                             :nimi ::t/data-sisalto
                                             :luokat #{"piilotettu"}
                                             :osat (vec (repeatedly 12
                                                          (fn []
                                                            {:tyyppi :rivi
                                                             :osat [{:tyyppi :teksti
                                                                     :luokat #{"table-default"}
                                                                     :fmt ks-yhteiset/aika-tekstilla-fmt}
                                                                    (ks-yhteiset/syote-solu {:nappi? true :fmt ks-yhteiset/summa-formatointi-uusi :paivitettava-asia :aseta-tunnit!
                                                                                             :solun-index 1
                                                                                             :blur-tallenna! (partial blur-tallenna! false rivin-nimi)
                                                                                             :nappia-painettu-tallenna! nappia-painettu-tallenna!})
                                                                    {:tyyppi :tyhja}
                                                                    {:tyyppi :tyhja}
                                                                    {:tyyppi :tyhja}]})))}]}))
                            (range 1 (inc t/jh-korvausten-omiariveja-lkm)))
        g (g-pohjat/uusi-taulukko {:header [{:tyyppi :teksti
                                             :leveys 4
                                             :luokat #{"table-default" "table-default-header" "lihavoitu"}}
                                            {:tyyppi :teksti
                                             :leveys 3
                                             :luokat #{"table-default" "table-default-header" "lihavoitu"}}
                                            {:tyyppi :teksti
                                             :leveys 3
                                             :luokat #{"table-default" "table-default-header" "lihavoitu"}}
                                            {:tyyppi :teksti
                                             :leveys 3
                                             :luokat #{"table-default" "table-default-header" "lihavoitu"}}
                                            {:tyyppi :teksti
                                             :leveys 1
                                             :luokat #{"table-default" "table-default-header" "lihavoitu"}}]
                                   :body (vec (concat vakiorivit
                                                muokattavat-rivit))
                                   :taulukon-id taulukon-id
                                   :root-luokat #{"salli-ylipiirtaminen"}
                                   :root-asetus! (fn [g]
                                                   (e! (tuck-apurit/->MuutaTila [:gridit :johto-ja-hallintokorvaukset :grid] g)))
                                   :root-asetukset {:haku (fn [] (get-in @tila/suunnittelu-kustannussuunnitelma [:gridit :johto-ja-hallintokorvaukset :grid]))
                                                    :paivita! (fn [f]
                                                                (swap! tila/suunnittelu-kustannussuunnitelma
                                                                  (fn [tila]
                                                                    (update-in tila [:gridit :johto-ja-hallintokorvaukset :grid] f))))}})

        [vakio-viimeinen-index vakiokasittelijat]
        (reduce (fn [[index grid-kasittelijat] {:keys [toimenkuva maksukausi hoitokaudet]}]
                  (let [yksiloiva-nimen-paate (str "-" toimenkuva "-" maksukausi)]
                    [(inc index) (merge grid-kasittelijat
                                   (if (t/toimenpide-koskee-ennen-urakkaa? hoitokaudet)
                                     {[::g-pohjat/data ::t/data-yhteenveto]
                                      (yhteenveto-grid-rajapinta-asetukset toimenkuva maksukausi true)}

                                     {[::g-pohjat/data index ::t/data-yhteenveto]
                                      (yhteenveto-grid-rajapinta-asetukset toimenkuva maksukausi false)

                                      [::g-pohjat/data index ::t/data-sisalto]
                                      {:rajapinta (keyword (str "johto-ja-hallintokorvaus" yksiloiva-nimen-paate))
                                       :solun-polun-pituus 2
                                       :jarjestys [{:keyfn :aika
                                                    :comp (fn [aika-1 aika-2]
                                                            (pvm/ennen? aika-1 aika-2))}
                                                   ^{:nimi :mapit} [:aika :tunnit :tuntipalkka :yhteensa :kk-v]]
                                       :datan-kasittely (fn [vuoden-jh-korvaukset]
                                                          (mapv (fn [rivi]
                                                                  (mapv (fn [[_ v]]
                                                                          v)
                                                                    rivi))
                                                            vuoden-jh-korvaukset))
                                       :tunnisteen-kasittely (fn [data-sisalto-grid data]
                                                               (vec
                                                                 (map-indexed (fn [i rivi]
                                                                                (vec
                                                                                  (map-indexed (fn [j osa]
                                                                                                 (when (instance? g-pohjat/SyoteTaytaAlas osa)
                                                                                                   {:osa :tunnit
                                                                                                    :osan-paikka [i j]
                                                                                                    :toimenkuva toimenkuva
                                                                                                    :maksukausi maksukausi}))
                                                                                    (grid/hae-grid rivi :lapset))))
                                                                   (grid/hae-grid data-sisalto-grid :lapset))))}}))]))
          [0 {}]
          (t/pohjadatan-versio))

        muokkauskasittelijat (second
                               (reduce (fn [[rivi-index grid-kasittelijat] nimi-index]
                                         (let [rivin-nimi (t/jh-omienrivien-nimi nimi-index)]
                                           [(inc rivi-index) (merge grid-kasittelijat
                                                               {[::g-pohjat/data rivi-index ::t/data-yhteenveto]
                                                                (muokkausrivien-rajapinta-asetukset rivin-nimi)

                                                                [::g-pohjat/data rivi-index ::t/data-sisalto]
                                                                {:rajapinta (keyword (str "johto-ja-hallintokorvaus-" rivin-nimi))
                                                                 :solun-polun-pituus 2
                                                                 :jarjestys [{:keyfn :aika
                                                                              :comp (fn [aika-1 aika-2]
                                                                                      (pvm/ennen? aika-1 aika-2))}
                                                                             ^{:nimi :mapit} [:aika :tunnit :tuntipalkka :yhteensa :maksukausi]]
                                                                 :datan-kasittely (fn [vuoden-jh-korvaukset]
                                                                                    (mapv (fn [rivi]
                                                                                            (mapv (fn [[_ v]]
                                                                                                    v)
                                                                                              rivi))
                                                                                      vuoden-jh-korvaukset))
                                                                 :tunnisteen-kasittely
                                                                 (fn [data-sisalto-grid data]
                                                                   (vec
                                                                     (map-indexed
                                                                       (fn [i rivi]
                                                                         (vec
                                                                           (map (fn [j osa]
                                                                                  (when (instance? g-pohjat/SyoteTaytaAlas osa)
                                                                                    {:osa :tunnit
                                                                                     :osan-paikka [i j]
                                                                                     :omanimi rivin-nimi}))
                                                                             (range)
                                                                             (grid/hae-grid rivi :lapset))))
                                                                       (grid/hae-grid data-sisalto-grid :lapset))))}})]))
                                 [vakio-viimeinen-index {}]
                                 (range 1 (inc t/jh-korvausten-omiariveja-lkm))))]

    ;; --- Datan käsittelijän ja grid-käsittelijän yhdistäminen rajapinnan kautta --
    (grid/rajapinta-grid-yhdistaminen! g
      ;; ### Rajapinta
      t/johto-ja-hallintokorvaus-rajapinta

      ;; ### Datan käsittelijä
      (t/johto-ja-hallintokorvaus-dr)

      ;; ### Grid käsittelijä
      (merge {[::g-pohjat/otsikko] {:rajapinta :otsikot
                                    :solun-polun-pituus 1
                                    :jarjestys [^{:nimi :mapit} [:toimenkuva :tunnit :tuntipalkka :yhteensa :kk-v]]
                                    :datan-kasittely (fn [otsikot]
                                                       (mapv (fn [otsikko]
                                                               otsikko)
                                                         (vals otsikot)))}}

        vakiokasittelijat
        muokkauskasittelijat))

    ;; --- Grid tapahtumat ---
    (grid/grid-tapahtumat g
      tila/suunnittelu-kustannussuunnitelma
      (merge {
              ;; Disabloi toimenkuvan päärivin tai toimenkuvan alitaulukon rivit
              :johto-ja-hallintokorvaukset-disablerivit
              {:polut [[:gridit :johto-ja-hallintokorvaukset :kuukausitasolla?]]
               :toiminto! (fn [g _ kuukausitasolla-kaikki-toimenkuvat]
                            (doseq [[toimenkuva maksukaudet-kuukausitasolla?] kuukausitasolla-kaikki-toimenkuvat]
                              ;; Jos totta, niin kyseessä on oma/itsetäytettävärivi
                              (when-not (and (string? toimenkuva) (re-find #"\d$" toimenkuva))
                                (doseq [[maksukausi kuukausitasolla?] maksukaudet-kuukausitasolla?
                                        :let [index (first (keep-indexed (fn [index jh-pohjadata]
                                                                           (when (and (= toimenkuva (:toimenkuva jh-pohjadata))
                                                                                   (= maksukausi (:maksukausi jh-pohjadata)))
                                                                             index))
                                                             (t/pohjadatan-versio)))]]
                                  (rividisable! g index kuukausitasolla?)))))}

              ;; Näyttää tai piilottaa "ennen urakkaa"-rivit. VHAR-3127
              :nayta-tai-piilota-ennen-urakkaa-rivit
              {:polut [[:suodattimet :hoitokauden-numero]]
               :toiminto! (fn [g _ hoitovuoden-nro]
                            ;; FIXME: ::t/data-yhteenveto nimi ainoalle "ennen urakkaa"-tyyppiselle taulukon riville on tosi hämäävä.
                            ;;        Vaikka kyse on vain yhdestä rivistä taulukossa, pitäisi olla rivillä parempi tunniste, varsinkin jos tuon tyyppisiä rivejä
                            ;;        tulee vielä joskus lisää...
                            ;;        Yritin muuttaa rivin nimeä ::t/data-yhteevedosta muuksi, mutta sen jälkeen tuli hankalasti debuggatavia erroreita, joten en vienyt
                            ;;         asiaa eteenpäin.

                            ;; NOTE: ::g-pohjat/data täytyy olla polun alussa, koska taulukko on luotu "g-pohjat/uusi-taulukko"-apufunktion avulla.
                            ;;       Taulukon rivit tulevat tällöin ::g-pohjat/data alle.
                            (let [rivi (grid/get-in-grid g [::g-pohjat/data ::t/data-yhteenveto])
                                  ;; Piilotetaan "Ennen urakkaa"-rivi mikäli hoitovuosi ei ole ensimmäinen. VHAR-3127
                                  piilotetaan? (not= hoitovuoden-nro 1)]
                              (when (grid/rivi? rivi)
                                (if piilotetaan?
                                  (grid/piilota! rivi)
                                  (grid/nayta! rivi))
                                (t/paivita-raidat! g))))}

              :lisaa-yhteenvetorivi
              {:polut (reduce (fn [polut jarjestysnumero]
                                (let [nimi (t/jh-omienrivien-nimi jarjestysnumero)]
                                  (conj polut [:domain :johto-ja-hallintokorvaukset nimi])))
                        []
                        (range 1 (inc t/jh-korvausten-omiariveja-lkm)))
               :toiminto! (fn [_ data & oma-data]
                            (let [omien-rivien-tiedot (transduce (comp (map ffirst)
                                                                   (map-indexed #(assoc %2 :jarjestysnumero (inc %1))))
                                                        conj
                                                        []
                                                        oma-data)
                                  lisatyt-rivit (get-in data [:gridit :johto-ja-hallintokorvaukset-yhteenveto :lisatyt-rivit] #{})]
                              (doseq [{:keys [toimenkuva toimenkuva-id jarjestysnumero]} omien-rivien-tiedot]
                                (cond

                                  (and (not (contains? lisatyt-rivit toimenkuva-id))
                                    (not (empty? toimenkuva)))
                                  (let [omanimi (t/jh-omienrivien-nimi jarjestysnumero)
                                        lisattava-rivi (grid/aseta-nimi
                                                         (grid/samanlainen-osa
                                                           (grid/get-in-grid (get-in data [:gridit :johto-ja-hallintokorvaukset-yhteenveto :grid])
                                                             [::g-pohjat/data 0]))
                                                         omanimi)
                                        rivin-paikka-index (count (grid/hae-grid
                                                                    (grid/get-in-grid
                                                                      (get-in data [:gridit :johto-ja-hallintokorvaukset-yhteenveto :grid])
                                                                      [::g-pohjat/data])
                                                                    :lapset))]
                                    (e! (tuck-apurit/->PaivitaTila
                                          [:gridit :johto-ja-hallintokorvaukset-yhteenveto :lisatyt-rivit]
                                          (fn [lisatyt-rivit]
                                            (conj (or lisatyt-rivit #{}) toimenkuva-id))))

                                    (grid/lisaa-rivi!
                                      (get-in data [:gridit :johto-ja-hallintokorvaukset-yhteenveto :grid])
                                      lisattava-rivi
                                      [1 rivin-paikka-index])

                                    (grid/lisaa-uuden-osan-rajapintakasittelijat
                                      (grid/get-in-grid (get-in data [:gridit :johto-ja-hallintokorvaukset-yhteenveto :grid])
                                        [1 rivin-paikka-index])
                                      [:. ::t/yhteenveto] {:rajapinta (keyword (str "yhteenveto-" omanimi))
                                                           :solun-polun-pituus 1
                                                           :datan-kasittely identity})

                                    (t/paivita-raidat! (get-in data [:gridit :johto-ja-hallintokorvaukset-yhteenveto :grid])))

                                  ;; --

                                  (and (contains? lisatyt-rivit toimenkuva-id)
                                    (empty? toimenkuva))
                                  (let [omanimi (t/jh-omienrivien-nimi jarjestysnumero)
                                        g (get-in data [:gridit :johto-ja-hallintokorvaukset-yhteenveto :grid])
                                        poistettava-rivi (grid/get-in-grid g
                                                           [::g-pohjat/data omanimi])]
                                    (e! (tuck-apurit/->PaivitaTila [:gridit :johto-ja-hallintokorvaukset-yhteenveto :lisatyt-rivit]
                                          (fn [lisatyt-rivit]
                                            (disj (or lisatyt-rivit #{}) toimenkuva-id))))
                                    (grid/poista-osan-rajapintakasittelijat poistettava-rivi)
                                    (grid/poista-rivi! g poistettava-rivi))

                                  :else nil))))}}

        (reduce (fn [polut jarjestysnumero]
                  (let [nimi (t/jh-omienrivien-nimi jarjestysnumero)]
                    (merge polut
                      {(keyword "piilota-itsetaytettyja-riveja-" nimi)
                       {:polut [[:gridit :johto-ja-hallintokorvaukset :yhteenveto nimi :maksukausi]]
                        :toiminto! (fn [g _ maksukausi]
                                     (let [naytettavat-kuukaudet (into #{} (mhu/maksukausi->kuukaudet-range maksukausi))]
                                       (doseq [rivi (grid/hae-grid (grid/get-in-grid (grid/etsi-osa g nimi) [1]) :lapset)]
                                         (let [aika (grid/solun-arvo (grid/get-in-grid rivi [0]))
                                               piilotetaan? (and aika (not (contains? naytettavat-kuukaudet (pvm/kuukausi aika))))]
                                           (if piilotetaan?
                                             (grid/piilota! rivi)
                                             (grid/nayta! rivi))))
                                       (t/paivita-raidat! (grid/osa-polusta g [::g-pohjat/data]))))}

                       (keyword "omarivi-disable-" nimi)
                       {:polut [[:domain :johto-ja-hallintokorvaukset nimi]
                                [:gridit :johto-ja-hallintokorvaukset :kuukausitasolla? nimi]]
                        :toiminto! (fn [g tila oma-jh-korvausten-tila kuukausitasolla?]
                                     (let [hoitokauden-numero (get-in tila [:suodattimet :hoitokauden-numero])
                                           toimenkuva (get-in oma-jh-korvausten-tila [(dec hoitokauden-numero) 0 :toimenkuva])
                                           index (dec (+ (count (t/pohjadatan-versio))
                                                        jarjestysnumero))
                                           yhteenvetorivi (if (grid/rivi? (grid/get-in-grid g [::g-pohjat/data index ::t/data-yhteenveto 0]))
                                                            (grid/get-in-grid g [::g-pohjat/data index ::t/data-yhteenveto 0])
                                                            (grid/get-in-grid g [::g-pohjat/data index ::t/data-yhteenveto]))]
                                       (if (empty? toimenkuva)
                                         (do
                                           (ks-yhteiset/maara-solujen-disable! (grid/get-in-grid g [::g-pohjat/data index ::t/data-sisalto])
                                             true)
                                           (disable-osa-indexissa! yhteenvetorivi #{1 2 4} true))
                                         (do (rividisable! g
                                               (dec (+ (count (t/pohjadatan-versio))
                                                      jarjestysnumero))
                                               kuukausitasolla?)
                                             (disable-osa-indexissa! yhteenvetorivi #{2 4} false)))))}})))
          {}
          (range 1 (inc t/jh-korvausten-omiariveja-lkm)))))


    ;; --- Triggeröi gridin luomisen jälkeen tarvittavat eventit ----
    (grid/triggeroi-tapahtuma! g :nayta-tai-piilota-ennen-urakkaa-rivit)
    (grid/triggeroi-tapahtuma! g :johto-ja-hallintokorvaukset-disablerivit)

    ;; --- Palauta aina grid ---
    g))

(defn johto-ja-hallintokorvaus-laskulla-yhteenveto-grid []
  (let [taulukon-id "johto-ja-hallintokorvaus-yhteenveto-taulukko"
        g (g-pohjat/uusi-taulukko
            {:header (-> (vec (repeat (if (t/post-2022?) 6 7)
                                {:tyyppi :teksti
                                 :leveys 5
                                 :luokat #{"table-default" "table-default-header" "lihavoitu"}}))
                       (assoc-in [0 :leveys] 10)
                       (assoc-in [1 :leveys] 4))
             :body (mapv (fn [_]
                           {:tyyppi :taulukko
                            :osat [{:tyyppi :rivi
                                    :nimi ::t/yhteenveto
                                    :osat (cond->
                                              (vec (repeat (if (t/post-2022?) 6 7)
                                                   {:tyyppi :teksti
                                                    :luokat #{"table-default"}
                                                    :fmt ks-yhteiset/summa-formatointi-uusi}))
                                            true (assoc-in [0 :fmt] nil)

                                            (not (t/post-2022?))
                                            (assoc-in [1 :fmt] 
                                              (fn [teksti]
                                                (if (nil? teksti)
                                                  ""
                                                  (let [sisaltaa-erottimen? (boolean (re-find #",|\." (str teksti)))]
                                                    (if sisaltaa-erottimen?
                                                      (fmt/desimaaliluku (clj-str/replace (str teksti) "," ".") 1 true)
                                                      teksti))))))}]})
                     (t/pohjadatan-versio))
             :footer (let [osat (vec (repeat (if (t/post-2022?) 6 7)
                                   {:tyyppi :teksti
                                    :luokat #{"table-default" "table-default-sum"}
                                    :fmt ks-yhteiset/yhteenveto-format}))]
                       (if-not (t/post-2022?)
                         (assoc osat 1 {:tyyppi :tyhja
                                   :luokat #{"table-default" "table-default-sum"}})
                         osat))
             :taulukon-id taulukon-id
             :root-asetus! (fn [g]
                             (e! (tuck-apurit/->MuutaTila
                                   [:gridit :johto-ja-hallintokorvaukset-yhteenveto :grid] g)))
             :root-asetukset {:haku (fn [] (get-in @tila/suunnittelu-kustannussuunnitelma
                                             [:gridit :johto-ja-hallintokorvaukset-yhteenveto :grid]))
                              :paivita! (fn [f]
                                          (swap! tila/suunnittelu-kustannussuunnitelma
                                            (fn [tila]
                                              (update-in tila [:gridit :johto-ja-hallintokorvaukset-yhteenveto :grid] f))))}})
        g (grid/lisaa-rivi! g
            (grid/rivi {:osat
                        (let [osat
                                    (vec (repeatedly (if (t/post-2022?) 6 7)
                                           (fn []
                                             (solu/teksti {:parametrit {:class #{"table-default" "table-default-sum" "harmaa-teksti"}}
                                                           :fmt ks-yhteiset/yhteenveto-format}))))]
                                (if-not (t/post-2022?)
                                  (update-in osat [1] gov/teksti->tyhja #{"table-default" "table-default-sum" "harmaa-teksti"})
                                  osat))
                        :koko {:seuraa {:seurattava ::g-pohjat/otsikko
                                        :sarakkeet :sama
                                        :rivit :sama}}
                        :nimi ::t/indeksikorjattu}
              [{:sarakkeet [0 (if (t/post-2022?) 6 7)] :rivit [0 1]}])
            [3])]

    (grid/rajapinta-grid-yhdistaminen! g
      t/johto-ja-hallintokorvaus-yhteenveto-rajapinta
      (t/johto-ja-hallintokorvaus-yhteenveto-dr)
      (let [kuvaus (vec (concat [:toimenkuva] (when-not (t/post-2022?) [:kk-v]) [:hoitovuosi-1 :hoitovuosi-2 :hoitovuosi-3 :hoitovuosi-4 :hoitovuosi-5]))]
        (merge {[::g-pohjat/otsikko] {:rajapinta :otsikot
                                           :solun-polun-pituus 1
                                      :jarjestys [(with-meta kuvaus {:nimi :mapit})]
                                           :datan-kasittely (fn [otsikot]
                                                              (mapv (fn [otsikko]
                                                                      otsikko)
                                                                (vals otsikot)))}
                     [::g-pohjat/yhteenveto] {:rajapinta :yhteensa
                                              :solun-polun-pituus 1
                                              :datan-kasittely identity}
                     [::t/indeksikorjattu] {:rajapinta :indeksikorjattu
                                            :solun-polun-pituus 1
                                            :datan-kasittely identity}}

               (second
                 (reduce (fn [[index grid-kasittelijat] {:keys [toimenkuva maksukausi]}]
                           (let [yksiloiva-nimen-paate (str "-" toimenkuva "-" maksukausi)]
                             [(inc index) (merge grid-kasittelijat
                                            {[::g-pohjat/data index ::t/yhteenveto] {:rajapinta (keyword (str "yhteenveto" yksiloiva-nimen-paate))
                                                                                     :solun-polun-pituus 1
                                                                                     :datan-kasittely identity}})]))
                   [0 {}]
                   (t/pohjadatan-versio))))))))


;; | -- Gridit päättyy



;; -----
;; -- Johto- ja hallintokorvaus osion apufunktiot --

(defn johto-ja-hallintokorvaukset-yhteensa
  "Laskee yhteen 'johto-ja-hallintokorvaukset' yhteenvedon summat (eli käytännössä tuntipalkat) ja 'toimistokulut' yhteenvedon summat.
  Laskee yhteissumman joko annetulle hoitovuodelle tai annettujen summavektorien jokaiselle hoitovuodelle.
  Jos hoitovuotta ei anneta, palauttaa vektorin, jossa on summa jokaisesssa elementissä."
  ([johto-ja-hallintokorvaukset-summat toimistokulut-summat]
   (johto-ja-hallintokorvaukset-yhteensa johto-ja-hallintokorvaukset-summat toimistokulut-summat nil))
  ([johto-ja-hallintokorvaukset-summat toimistokulut-summat hoitokausi]
   (assert (or (nil? hoitokausi) (number? hoitokausi)) "Hoitokausi ei ole numero!")
   (assert (vector? johto-ja-hallintokorvaukset-summat) "johto-ja-hallintokorvaukset-summat täytyy olla vektori.")
   (assert (vector? toimistokulut-summat) "toimistokulut-summat täytyy olla vektori.")

   (if hoitokausi
     (let [hoitokausi-idx (dec hoitokausi)]
       (+
         (nth johto-ja-hallintokorvaukset-summat hoitokausi-idx)
         (nth toimistokulut-summat hoitokausi-idx)))

     (mapv (fn [jh tk]
             (+ jh tk))
       johto-ja-hallintokorvaukset-summat
       toimistokulut-summat))))

(defn- johto-ja-hallintokorvaus-yhteenveto
  [johto-ja-hallintokorvaukset-summat toimistokulut-summat johto-ja-hallintokorvaukset-indeksikorjattu
   toimistokulut-indeksikorjattu kuluva-hoitokausi indeksit kantahaku-valmis?]
  (if (and indeksit kantahaku-valmis?)
    (let [hinnat (mapv (fn [summa]
                         {:summa summa})
                   (johto-ja-hallintokorvaukset-yhteensa johto-ja-hallintokorvaukset-summat toimistokulut-summat))
          hinnat-indeksikorjattu (mapv (fn [summa]
                                         {:summa summa})
                                   (johto-ja-hallintokorvaukset-yhteensa
                                     johto-ja-hallintokorvaukset-indeksikorjattu
                                     toimistokulut-indeksikorjattu))]
      [:div.summa-ja-indeksilaskuri
       [ks-yhteiset/hintalaskuri
        {:otsikko nil
         :selite "Palkat + Toimisto- ja ICT-kulut, tiedotus, opastus, kokousten järj. jne. + Hoito- ja korjaustöiden pientarvikevarasto"
         :hinnat hinnat
         :data-cy "johto-ja-hallintokorvaus-hintalaskuri"}
        kuluva-hoitokausi]
       [ks-yhteiset/indeksilaskuri-ei-indeksikorjausta
        hinnat-indeksikorjattu indeksit
        {:data-cy "johto-ja-hallintokorvaus-indeksilaskuri"}]])
    [yleiset/ajax-loader]))

(defn- laske-vuoden-summa
  [hoitokauden-kuukausittaiset-summat]
  (let [summat-vuodelle (map
                          #(-> % second :kuukausipalkka)
                          hoitokauden-kuukausittaiset-summat)]
    (reduce + 0 summat-vuodelle)))

(defn formatoi-kuukausi
  [vuosi kuukausi]
  (let [tanaan (pvm/nyt)]
    (str
      (get {1 "Tammikuu"
            2 "Helmikuu"
            3 "Maaliskuu"
            4 "Huhtikuu"
            5 "Toukokuu"
            6 "Kesäkuu"
            7 "Heinäkuu"
            8 "Elokuu"
            9 "Syyskuu"
            10 "Lokakuu"
            11 "Marraskuu"
            12 "Joulukuu"}
        kuukausi)
      " " (if (> kuukausi 9)
            vuosi
            (inc vuosi))
      (when 
          (or
            (< (inc vuosi) (-> tanaan pvm/vuosi))
            
            (and
              (= (inc vuosi) (-> tanaan pvm/vuosi))
              (or (< kuukausi (-> tanaan pvm/kuukausi))
                (< 9 kuukausi )))

            (and                    
              (= vuosi (-> tanaan pvm/vuosi))                
              (and (< kuukausi (-> tanaan pvm/kuukausi))
                (< 9 kuukausi))))          
        " (mennyt)"))))

(defn- kun-erikseen-syotettava-checkbox-klikattu
  [checkbox-tila]
  @checkbox-tila)

(defn- jaa-vuosipalkka-kuukausille
  [hoitokausi kopioi-tuleville? ennen-urakkaa? toimenkuvan-maarat vuosipalkka]
  (if ennen-urakkaa?
    (assoc-in toimenkuvan-maarat [1 10 :kuukausipalkka] vuosipalkka) ;; ennen urakkaa tapahtuvat laitetaan ekalle lokakuulle
    (let [kuukausipalkka (tyokalut/round2 2 (/ vuosipalkka 12))
          viimeinen-kuukausi (if-not (= vuosipalkka (* 12 kuukausipalkka))
                               (tyokalut/round2 2 (- vuosipalkka (tyokalut/round2 2 (* 11 kuukausipalkka))))
                               kuukausipalkka)
          alkuvuosi (-> tila/yleiset deref :urakka :alkupvm pvm/vuosi)
          loppuvuosi (-> tila/yleiset deref :urakka :loppupvm pvm/vuosi)
          loppukausi (inc
                       (if kopioi-tuleville?
                         (- loppuvuosi alkuvuosi)
                         hoitokausi))
          kaudet (into []
                   (range hoitokausi loppukausi))
          aseta-kuukausipalkka (map (fn [[kuukausi tiedot]]
                                      [kuukausi (assoc tiedot :kuukausipalkka (if (= 9 kuukausi)
                                                                                viimeinen-kuukausi
                                                                                kuukausipalkka))]))
          paivita-vuoden-tiedot (fn [kkt]
                                  (into {}
                                    aseta-kuukausipalkka
                                    kkt))
          paivitysfunktiot (mapv
                             #(fn [maarat]
                                (update maarat %
                                  paivita-vuoden-tiedot))
                             kaudet)
          paivita (apply comp paivitysfunktiot)]
      (paivita toimenkuvan-maarat))))

(defn- tallenna-vuosipalkka
  [atomi {:keys [hoitokausi kopioi-tuleville?]} rivi]
  (let [toimenkuvan-maarat (get-in rivi [:maksuerat-per-hoitovuosi-per-kuukausi])
        paivitetyt (jaa-vuosipalkka-kuukausille hoitokausi kopioi-tuleville? (:ennen-urakkaa? rivi) toimenkuvan-maarat (:vuosipalkka rivi))
        rivi (assoc rivi :maksuerat-per-hoitovuosi-per-kuukausi paivitetyt)
        tiedot @atomi
        tiedot-muuttuneet? (not= 
                             (get tiedot (:tunniste rivi))
                             rivi)
        tiedot (assoc-in tiedot [(:tunniste rivi) :maksuerat-per-hoitovuosi-per-kuukausi] paivitetyt)
        _ (reset! atomi tiedot)]
    ;; Atomin päivittämisessä menee joitakin millisekunteja. Siksi viive
    (when tiedot-muuttuneet?
      (yleiset/fn-viiveella #(e! (t/->TallennaJHOToimenkuvanVuosipalkka rivi)) 1))))

(defn- paivita-toimenkuvan-vuosiloota
  [hoitokausi [toimenkuva toimenkuvan-tiedot]]
  (let [vuoden-summa (laske-vuoden-summa (get-in toimenkuvan-tiedot [:maksuerat-per-hoitovuosi-per-kuukausi hoitokausi]))]
    [toimenkuva (assoc toimenkuvan-tiedot :vuosipalkka vuoden-summa)]))

(defn- paivita-vuosilootat
  [tiedot hoitokausi]
  (into {}
    (map (r/partial paivita-toimenkuvan-vuosiloota hoitokausi))
    tiedot))

(defn- tallenna-kuukausipalkka
  [tiedot-atom {:keys [alkuvuosi loppuvuosi hoitokausi kopioi-tuleville?]} rivi]
  (let [tiedot @tiedot-atom
        loppukausi (inc
                     (if kopioi-tuleville?
                       (- loppuvuosi alkuvuosi)
                       hoitokausi))
        kaudet (into [] (range hoitokausi loppukausi))
        erat (get tiedot :maksuerat-per-hoitovuosi-per-kuukausi)
        paivitysfunktiot (mapv
                           (fn [kausi]
                             (fn [maarat]
                               (assoc-in maarat [kausi (:kuukausi rivi) :kuukausipalkka] (:kuukausipalkka rivi))))
                           kaudet)
        vuosipalkka (reduce (fn [summa kuukauden-arvot]
                              (if (not (nil? (:kuukausipalkka (second kuukauden-arvot))))
                                (+ summa (:kuukausipalkka (second kuukauden-arvot)))
                                summa))
                      0
                      (get erat hoitokausi))
        paivita (apply comp paivitysfunktiot)
        paivitetyt (paivita erat)
        tiedot (assoc tiedot :vuosipalkka vuosipalkka)
        toimenkuvan-tiedot (assoc tiedot :maksuerat-per-hoitovuosi-per-kuukausi paivitetyt)
        _ (reset! tiedot-atom toimenkuvan-tiedot)]
    ;; Atomin päivittämisessä menee joitakin millisekunteja. Siksi viive
    (yleiset/fn-viiveella #(e! (t/->TallennaJHOToimenkuvanVuosipalkka toimenkuvan-tiedot)) 1)))

(defn- jarjesta-hoitovuoden-jarjestykseen
  "Järjestys lokakuusta seuraavan vuoden syyskuuhun"
  [{:keys [kuukausi]}]
  (if (> kuukausi 9)
    (- kuukausi 12)
    kuukausi))

(defn- luo-kursori
  [atomi toimenkuva polku hoitokausi]
  (r/cursor atomi [toimenkuva polku hoitokausi]))

(defn- kursorit-polulle
  [polku tiedot toimenkuva vuosien-erotus]
  (into {}
    (map (juxt
           identity
           (r/partial luo-kursori tiedot toimenkuva polku)))
    (range 1 vuosien-erotus)))

(defn- palkkakentta-muokattava?
  [erikseen-syotettava? tiedot hoitokausi]
  (and @erikseen-syotettava?
    (or
      (and
         (:ennen-urakkaa? tiedot)
         (= 1 hoitokausi))
      (false? (:ennen-urakkaa? tiedot)))))

(defn- vetolaatikko-klikattu-fn [atomi tallenna-fn rivi-atom event]
  (let [valittu? (-> event .-target .-checked)]
    (when (and @atomi (not valittu?))
      (tallenna-fn @rivi-atom))
    (reset! atomi valittu?)))

(defn- vetolaatikko-komponentti
  [tiedot-atomi polku {:keys [tunniste] :as rivi} tallenna-fn_ kuluva-hoitokausi_]
  (let [suodattimet (r/cursor tila/suunnittelu-kustannussuunnitelma [:suodattimet])
        urakan-alkuvuosi (-> tila/yleiset deref :urakka :alkupvm pvm/vuosi)
        urakan-loppuvuosi (-> tila/yleiset deref :urakka :loppupvm pvm/vuosi)
        vuosien-erotus (- urakan-loppuvuosi urakan-alkuvuosi)
        kursorit (assoc {}
                   :toimenkuva (r/cursor tiedot-atomi [tunniste])
                   :maksuerat (kursorit-polulle :maksuerat-per-hoitovuosi-per-kuukausi tiedot-atomi polku vuosien-erotus)
                   :erikseen-syotettava? (kursorit-polulle :erikseen-syotettava? tiedot-atomi polku vuosien-erotus))]
    (fn [_ polku rivi tallenna-fn kuluva-hoitokausi]
      (let [{kopioi-tuleville? :kopioidaan-tuleville-vuosille?} @suodattimet
            valitun-hoitokauden-alkuvuosi (-> @tila/yleiset :urakka :alkupvm pvm/vuosi (+ (dec kuluva-hoitokausi)))
            maksuerat (get-in kursorit [:maksuerat kuluva-hoitokausi])]

        [:div
         [kentat/tee-kentta {:tyyppi :checkbox
                             :teksti "Suunnittele maksuerät kuukausittain"
                             :valitse! (partial
                                         vetolaatikko-klikattu-fn
                                         (get-in kursorit [:erikseen-syotettava? kuluva-hoitokausi])
                                         tallenna-fn
                                         (get-in kursorit [:toimenkuva]))}
          (get-in kursorit [:erikseen-syotettava? kuluva-hoitokausi])]
         [:div.vetolaatikko-border {:style {:border-left "4px solid lightblue" :margin-top "16px" :padding-left "18px"}}
          [vanha-grid/muokkaus-grid
           {:id (str polku valitun-hoitokauden-alkuvuosi)
            :voi-poistaa? (constantly false)
            :voi-lisata? false
            :piilota-toiminnot? true
            :muokkauspaneeli? false
            :jarjesta jarjesta-hoitovuoden-jarjestykseen
            :piilota-table-header? true
            :on-rivi-blur (r/partial tallenna-kuukausipalkka
                            (get kursorit :toimenkuva)
                            {:hoitokausi kuluva-hoitokausi
                             :kopioi-tuleville? kopioi-tuleville?
                             :alkuvuosi urakan-alkuvuosi
                             :loppuvuosi urakan-loppuvuosi})
            :voi-kumota? false}
           [{:nimi :kuukausi :tyyppi :string :muokattava? (constantly false) :leveys "85%" :fmt (r/partial formatoi-kuukausi valitun-hoitokauden-alkuvuosi)}
            {:nimi :kuukausipalkka :tyyppi :numero :leveys "15%" :muokattava? #(palkkakentta-muokattava? (get-in kursorit [:erikseen-syotettava? kuluva-hoitokausi]) rivi kuluva-hoitokausi)}]
           maksuerat]]]))))

(defn luo-vetolaatikot
  [atomi tallenna-fn kuluva-hoitokausi]
  (into {}
    (map
      (juxt first #(let [rivi (second %)
                         polku (:tunniste rivi)]
                     [vetolaatikko-komponentti atomi polku rivi tallenna-fn kuluva-hoitokausi])))
    (into {}
      (filter #(let [data (second %)]
                 (not (or (:ennen-urakkaa? data)
                        (get (:hoitokaudet data) 0)))))
      @atomi)))

(defn- kun-ei-syoteta-erikseen
  [hoitokausi tiedot]
  (and
    (not (get-in tiedot [:erikseen-syotettava? hoitokausi]))
    (or
      (and
         (:ennen-urakkaa? tiedot)
         (= 1 hoitokausi))
      (false? (:ennen-urakkaa? tiedot)))))

(defn tallenna-toimenkuvan-tiedot
  [data-atomi optiot rivin-tiedot]
  (when (:oma-toimenkuva? rivin-tiedot)
    (let [uusi-toimenkuva-nimi (:toimenkuva rivin-tiedot)
          rivin-nimi (:rivin-nimi rivin-tiedot)]
      ;; Jos toimenkuvan nimi muuttui, niin vaihdetaan se
      ;; FIXME: rivin-nimi ei koskaan vaihdu, joten tätä kutsutaan aina. Jätetään toistaiseksi, koska ei ole vakava virhe.
      (when (not= uusi-toimenkuva-nimi rivin-nimi)
        (e! (t/->VaihdaOmanToimenkuvanNimi rivin-tiedot))
        (e! (t/->TallennaToimenkuva (:tunniste rivin-tiedot))))))
  (tallenna-vuosipalkka
    data-atomi
    optiot
    rivin-tiedot))

(defn taulukko-2022-eteenpain
  [app]
  (let [kaytetty-hoitokausi (r/atom (-> app :suodattimet :hoitokauden-numero))
        data (t/konvertoi-jhk-data-taulukolle (get-in app [:domain :johto-ja-hallintokorvaukset]) @kaytetty-hoitokausi)]
    (fn [app]
      (let [kuluva-hoitokausi (-> app :suodattimet :hoitokauden-numero)
            kopioidaan-tuleville-vuosille? (-> app :suodattimet :kopioidaan-tuleville-vuosille?)
            tallenna-fn (r/partial tallenna-toimenkuvan-tiedot
                          data
                          {:hoitokausi kuluva-hoitokausi
                           :kopioi-tuleville? kopioidaan-tuleville-vuosille?})
            vetolaatikot (luo-vetolaatikot data tallenna-fn @kaytetty-hoitokausi)]
        (when-not (= kuluva-hoitokausi @kaytetty-hoitokausi)
          (swap! data paivita-vuosilootat kuluva-hoitokausi)
          (reset! kaytetty-hoitokausi kuluva-hoitokausi))
        [:div
         ;; Kaikille yhteiset toimenkuvat
         [vanha-grid/muokkaus-grid
          {:otsikko "Tuntimäärät ja -palkat"
           :id "toimenkuvat-taulukko"
           :luokat ["poista-bottom-margin"]
           :voi-lisata? false
           :voi-kumota? false
           :muutos (fn [g]
                     (when-not (= @kaytetty-hoitokausi kuluva-hoitokausi)
                       (vanha-grid/sulje-vetolaatikot! g)))
           :jarjesta :jarjestys
           :piilota-toiminnot? true
           :on-rivi-blur tallenna-fn
           :voi-poistaa? (constantly false)
           :piilota-rivi #(and (not (= kuluva-hoitokausi 1)) (:ennen-urakkaa? %))
           :vetolaatikot vetolaatikot}
          [{:otsikko "Toimenkuva" :nimi :toimenkuva :tyyppi :string :muokattava? :oma-toimenkuva? :leveys "80%" :fmt clj-str/capitalize :placeholder "Kirjoita muu toimenkuva"}
           {:otsikko "" :tyyppi :vetolaatikon-tila :leveys "5%" :muokattava? (constantly false)}
           {:otsikko "Vuosipalkka, €" :nimi :vuosipalkka :desimaalien-maara 2 :tyyppi :numero :muokattava? (r/partial kun-ei-syoteta-erikseen kuluva-hoitokausi) :leveys "15%"}]
          data]]))))

(defn- johto-ja-hallintokorvaus
  [app vahvistettu? johto-ja-hallintokorvaus-grid johto-ja-hallintokorvaus-yhteenveto-grid toimistokulut-grid
   suodattimet
   johto-ja-hallintokorvaukset-summat toimistokulut-summat
   johto-ja-hallintokorvaukset-summat-indeksikorjattu
   toimistokulut-summat-indeksikorjattu
   kuluva-hoitokausi
   indeksit
   kantahaku-valmis?]
  (let [alkuvuosi (-> tila/yleiset deref :urakka :alkupvm pvm/vuosi)]
    [:<>
     [:h2 {:id (str (get t/hallinnollisten-idt :johto-ja-hallintokorvaus) "-osio")} "Johto- ja hallintokorvaus"]
     [johto-ja-hallintokorvaus-yhteenveto
      johto-ja-hallintokorvaukset-summat toimistokulut-summat johto-ja-hallintokorvaukset-summat-indeksikorjattu
      toimistokulut-summat-indeksikorjattu kuluva-hoitokausi indeksit kantahaku-valmis?]

     ;; Tuntimäärät ja -palkat -taulukko
     [:h3 "Tuntimäärät ja -palkat"]
     [:div {:data-cy "tuntimaarat-ja-palkat-taulukko-suodattimet"}
      [ks-yhteiset/yleis-suodatin suodattimet]]
     (cond
       (and johto-ja-hallintokorvaus-grid kantahaku-valmis? (< alkuvuosi t/vertailuvuosi-uudelle-taulukolle))
       ;; FIXME: "Osio-vahvistettu" luokka on väliaikainen hack, jolla osion input kentät saadaan disabloitua kunnes muutosten seuranta ehditään toteuttaa.
       [:div {:class (when vahvistettu? "osio-vahvistettu")}
        [grid/piirra johto-ja-hallintokorvaus-grid]]

       (and (>= alkuvuosi t/vertailuvuosi-uudelle-taulukolle) johto-ja-hallintokorvaus-grid kantahaku-valmis?)
       [:div {:class (when vahvistettu? "osio-vahvistettu")}
        [taulukko-2022-eteenpain app]]

       :else
       [yleiset/ajax-loader])

     (if (and johto-ja-hallintokorvaus-yhteenveto-grid kantahaku-valmis?)
       ;; FIXME: "Osio-vahvistettu" luokka on väliaikainen hack, jolla osion input kentät saadaan disabloitua kunnes muutosten seuranta ehditään toteuttaa.
       [:div {:class (when vahvistettu? "osio-vahvistettu")}
        [grid/piirra johto-ja-hallintokorvaus-yhteenveto-grid]]
       [yleiset/ajax-loader])

     ;; Johto ja hallinto: Muut kulut -taulukko
     [:h3 {:id (str (get t/hallinnollisten-idt :toimistokulut) "-osio")} "Johto ja hallinto: muut kulut"]
     [:div {:data-cy "johto-ja-hallinto-muut-kulut-taulukko-suodattimet"}
      [ks-yhteiset/yleis-suodatin suodattimet]]

     ;; Note: "Muut kulut" taulukko on hämäävästi toimistokulut-grid. Jos gridiin tulee myöhemmin
     ;;       muutakin kuin pelkkä "toimistokulut", niin kannattaa harkita gridin nimen vaihtoa. Tämä on vähän työlästä, sillä
     ;;       gridin dataan viitataan monessa paikassa :toimistokulut-keywordillä.
     (if (and toimistokulut-grid kantahaku-valmis?)
       ;; FIXME: "Osio-vahvistettu" luokka on väliaikainen hack, jolla osion input kentät saadaan disabloitua kunnes muutosten seuranta ehditään toteuttaa.
       [:div {:class (when vahvistettu? "osio-vahvistettu")}
        [grid/piirra toimistokulut-grid]]
       [yleiset/ajax-loader])

     [:span
      "Yhteenlaskettu kk-määrä: Toimisto- ja ICT-kulut, tiedotus, opastus, kokousten ja vierailujen järjestäminen sekä tarjoilukulut + Hoito- ja korjaustöiden pientarvikevarasto (työkalut, mutterit, lankut, naulat jne.)"]]))

;; ### Johto- ja hallintokorvaus osion pääkomponentti ###

;; FIXME: Arvojen tallentamisessa on jokin häikkä. Tallennus ei onnistu. (Oli ennen ositustakin sama homma)
(defn osio
  [app
   vahvistettu?
   johto-ja-hallintokorvaus-grid
   johto-ja-hallintokorvaus-yhteenveto-grid
   toimistokulut-grid
   suodattimet
   johto-ja-hallintokorvaukset-summat
   toimistokulut-summat
   johto-ja-hallintokorvaukset-summat-indeksikorjattu
   toimistokulut-summat-indeksikorjattu
   kuluva-hoitokausi
   indeksit
   kantahaku-valmis?]
  [johto-ja-hallintokorvaus app
   vahvistettu?
   johto-ja-hallintokorvaus-grid johto-ja-hallintokorvaus-yhteenveto-grid toimistokulut-grid suodattimet
   johto-ja-hallintokorvaukset-summat toimistokulut-summat johto-ja-hallintokorvaukset-summat-indeksikorjattu
   toimistokulut-summat-indeksikorjattu kuluva-hoitokausi indeksit kantahaku-valmis?])



(ns harja.views.urakka.suunnittelu.kustannussuunnitelma.kustannussuunnitelma-view
  "Kustannussuunnitelma-sivun päänäkymä."
  (:require [reagent.core :as r :refer [atom]]
            [cljs.core.async :as async :refer [<! >! chan timeout]]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.suunnittelu.mhu-kustannussuunnitelma :as t]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.modal :as modal]
            [harja.ui.taulukko.grid-pohjat :as g-pohjat]
            [harja.ui.taulukko.grid :as grid]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :as yleiset]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.tyokalut.vieritys :as vieritys]
            [harja.ui.debug :as debug]
            [harja.ui.valinnat :as valinnat]
            [harja.domain.roolit :as roolit]
            [harja.tiedot.istunto :as istunto]
            [harja.domain.mhu :as mhu-domain]
            [harja.views.urakka.suunnittelu.kustannussuunnitelma.yhteiset :as ks-yhteiset :refer [e!]]
            [harja.views.urakka.suunnittelu.kustannussuunnitelma.osion-vahvistus :as osion-vahvistus]
            [harja.views.urakka.suunnittelu.kustannussuunnitelma.hankintakustannukset-osio :as hankintakustannukset-osio]
            [harja.views.urakka.suunnittelu.kustannussuunnitelma.erillishankinnat-osio :as erillishankinnat-osio]
            [harja.views.urakka.suunnittelu.kustannussuunnitelma.johto-ja-hallintokorvaus-osio :as johto-ja-hallintokorvaus-osio]
            [harja.views.urakka.suunnittelu.kustannussuunnitelma.hoidonjohtopalkkio-osio :as hoidonjohtopalkkio-osio]
            [harja.views.urakka.suunnittelu.kustannussuunnitelma.tavoite-ja-kattohinta-osio :as tavoite-ja-kattohinta-osio]
            [harja.views.urakka.suunnittelu.kustannussuunnitelma.tilaajan-rahavaraukset-osio :as tilaajan-rahavaraukset-osio]
            [harja.domain.mhu :as mhu])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))


;; Sivun yhteenvetomenu

(defn- summa-komp
  [osio-avain summien-maara m]
  [:div
   {:class ["sisalto"
            (when (and (= ::t/tavoite-ja-kattohinta osio-avain) (= 6 summien-maara)) "tavoite-ja-kattohinta")]}
   [:div.otsikko (str (or (:otsikko m)
                        "-"))]
   [:div.summa (str
                 (fmt/euro
                   (or (:summa m)
                     0)))]])

(defn- navigointivalikko
  "Navigointivalikko, joka näyttää vierityslinkit osioon ja tiedon siitä onko osio vahvistettu vai ei."
  [avaimet hoitokausi {:keys [urakka indeksit-saatavilla?]} tiedot]
  [:<>
   [:div.flex-row
    [:div
     [:h1 "Kustannussuunnitelma"]
     [:div.pieni-teksti urakka]]
    [valinnat/hoitovuosi-rivivalitsin (range 1 6) hoitokausi #(e! (tuck-apurit/->MuutaTila [:suodattimet :hoitokauden-numero] %1))]]
   [:div#tilayhteenveto.hintalaskuri
    ;; Taulukon rivit
    (into [:<>]
      (keep identity
        (mapcat identity
          (for [a avaimet]
            ;; Osion-rivin määrittely
            (let [{:keys [nimi suunnitelma-vahvistettu? summat nayta-osion-status?]
                   :or {nayta-osion-status? true} :as tieto} (a tiedot)
                  summat (mapv (partial summa-komp a (count summat)) summat)
                  {:keys [status-ikoni status-tyyppi status-teksti]}
                  (when nayta-osion-status?
                    (cond suunnitelma-vahvistettu?
                          {:status-ikoni ikonit/check :status-tyyppi ::yleiset/ok :status-teksti "Vahvistettu"}

                          indeksit-saatavilla?
                          {:status-ikoni ikonit/aika :status-tyyppi ::yleiset/info :status-teksti "Odottaa vahvistusta"}

                          :else
                          {:status-ikoni ikonit/exclamation-sign :status-tyyppi ::yleiset/huomio :status-teksti "Indeksejä ei vielä saatavilla"}))]
              (when tieto
                [[:div.osion-yhteenveto-rivi
                  {:data-cy (str "osion-yhteenveto-rivi-" nimi)}
                  [:div.flex-row.flex-wrap
                   [:div
                    [:div [yleiset/linkki (str nimi) (vieritys/vierita a)]]]
                   [:div
                    (when nayta-osion-status?
                      [:div [yleiset/infolaatikko status-ikoni status-teksti status-tyyppi]])]]
                  ;; Osion luvut
                  (vec (keep identity
                         (concat [:div {:class ["flex-row" "alkuun"  (when (= ::t/tavoite-ja-kattohinta a) "flex-wrap")]}]
                           summat)))]]))))))]])

(defn- laske-hankintakustannukset
  [hoitokausi suunnitellut laskutus varaukset]
  (let [indeksi (dec hoitokausi)
        kaikki (concat (mapcat vals [suunnitellut laskutus])
                 (mapcat vals (vals varaukset)))]
    (reduce #(+ %1 (nth %2 indeksi)) 0 kaikki)))

(defn- menukomponentti
  [{:keys [avaimet app indeksit-saatavilla?]}]
  (r/with-let
    [poisto-fn (fn [[avain solut]]
                 [avain (->> solut
                          (keep identity)
                          vec)])
     poista-nilit (fn [m]
                    (into {}
                      (mapv poisto-fn m)))]
    (if (:kantahaku-valmis? app)
      (let [hoitokausi (get-in app [:suodattimet :hoitokauden-numero])
            hoitokausi-idx (dec hoitokausi)
            indeksikerroin (get-in app [:domain :indeksit hoitokausi-idx :indeksikerroin])
            {{:keys [suunnitellut-hankinnat
                     laskutukseen-perustuvat-hankinnat
                     rahavaraukset] :as _summat} :summat :as _hankintakustannukset} (get-in app [:yhteenvedot :hankintakustannukset])
            ;; Talvihoito + Liikenneympäristön hoito + Sorateiden hoito + Päällystepaikkaukset + MHU Ylläpito + MHU Korvausinvestointi
            ;; https://knowledge.solita.fi/display/HAR/Kustannussuunnitelma-tab#Kustannussuunnitelmatab-Hankintakustannukset
            hankintakustannukset-summa (laske-hankintakustannukset
                                         hoitokausi
                                         suunnitellut-hankinnat
                                         laskutukseen-perustuvat-hankinnat
                                         rahavaraukset)
            erillishankinnat-summa (get-in app
                                     [:yhteenvedot :johto-ja-hallintokorvaukset :summat :erillishankinnat
                                      hoitokausi-idx])
            ;; Johto- ja hallintkorvaukset = Palkat + Toimisto- ja ICT-kulut, tiedotus, opastus, kokousten järj. jne. + Hoito- ja korjaustöiden pientarvikevarasto
            ;; Eli, "johto-ja-hallintokorvaus" + "toimistokulut" (eli nykyisin Johto ja hallinto: muut kulut)
            ;; https://knowledge.solita.fi/display/HAR/Kustannussuunnitelma-tab#Kustannussuunnitelmatab-Johto-jahallintokorvaus
            johto-ja-hallintokorvaukset-summa (johto-ja-hallintokorvaus-osio/johto-ja-hallintokorvaukset-yhteensa
                                                (get-in app [:yhteenvedot :johto-ja-hallintokorvaukset :summat :johto-ja-hallintokorvaukset])
                                                (get-in app [:yhteenvedot :johto-ja-hallintokorvaukset :summat :toimistokulut])
                                                ;; HOX: Käytetään suoraan hoitokauden numeroa, eikä "hoitokausi-idx".
                                                hoitokausi)
            hoidonjohtopalkkio-summa (get-in app
                                       [:yhteenvedot :johto-ja-hallintokorvaukset :summat :hoidonjohtopalkkio
                                        hoitokausi-idx])

            ;; Hankintakustannukset + Erillishankinnat + Johto- ja hallintokorvaus + Hoidonjohtopalkkio
            ;; https://knowledge.solita.fi/display/HAR/Kustannussuunnitelma-tab#Kustannussuunnitelmatab-Tavoitehinta
            tavoitehinta-summa (+
                                 hankintakustannukset-summa
                                 erillishankinnat-summa
                                 johto-ja-hallintokorvaukset-summa
                                 hoidonjohtopalkkio-summa)
            kattohinta-summa (or
                               (get-in app [:kattohinta :grid 0 (keyword (str "kattohinta-vuosi-" hoitokausi))])
                               (* tavoitehinta-summa 1.1))
            tilaajan-rahavaraukset-summa (get-in app
                                           ;; HOX: tilaajan-varaukset gridiin liittyvät polut poikkeavat suunnitelman osion nimestä "Tilaajan rahavaraukset"
                                           [:yhteenvedot :tilaajan-varaukset :summat :tilaajan-varaukset
                                            hoitokausi-idx])


            haettavat-osioiden-tilat #{:erillishankinnat :hankintakustannukset :hoidonjohtopalkkio
                                       :johto-ja-hallintokorvaus :tavoite-ja-kattohinta :tilaajan-rahavaraukset}
            suunnitelman-tilat (get-in app [:domain :osioiden-tilat])

            {hankintakustannukset-vahvistettu? :hankintakustannukset
             erillishankinnat-vahvistettu? :erillishankinnat
             johto-ja-hallintokorvaus-vahvistettu? :johto-ja-hallintokorvaus
             hoidonjohtopalkkio-vahvistettu? :hoidonjohtopalkkio
             tavoite-ja-kattohinta-vahvistettu? :tavoite-ja-kattohinta
             tilaajan-rahavaraukset-vahvistettu? :tilaajan-rahavaraukset}
            (into {} (mapv #(-> [% (get-in suunnitelman-tilat [% hoitokausi])])
                       haettavat-osioiden-tilat))


            ;; Oikaistut tavoite- ja kattohinnat ovat saatavilla budjettitavoitteesta.
            hoitokauden-budjettitavoite (first (filter #(= hoitokausi (:hoitokausi %)) (:budjettitavoite app)))
            tavoitehinta-oikaistu (when tavoite-ja-kattohinta-vahvistettu? (:tavoitehinta-oikaistu hoitokauden-budjettitavoite))
            kattohinta-oikaistu (when tavoite-ja-kattohinta-vahvistettu? (:kattohinta-oikaistu hoitokauden-budjettitavoite))
            tavoitehinta-indeksikorjattu (:tavoitehinta-indeksikorjattu hoitokauden-budjettitavoite)
            kattohinta-indeksikorjattu (:kattohinta-indeksikorjattu hoitokauden-budjettitavoite)

            {:keys [summa-hankinnat summa-erillishankinnat summa-hoidonjohtopalkkio summa-tilaajan-rahavaraukset
                    summa-johto-ja-hallintokorvaus summa-tavoite-ja-kattohinta]}
            (poista-nilit
              {:summa-hankinnat [{:otsikko "Yhteensä"
                                  :summa hankintakustannukset-summa}
                                 (when indeksit-saatavilla? {:otsikko "Indeksikorjattu"
                                                             :summa (* hankintakustannukset-summa indeksikerroin)})]
               :summa-erillishankinnat [{:otsikko "Yhteensä"
                                         :summa erillishankinnat-summa}
                                        (when indeksit-saatavilla?
                                          {:otsikko "Indeksikorjattu"
                                           :summa (* erillishankinnat-summa indeksikerroin)})]
               :summa-hoidonjohtopalkkio [{:otsikko "Yhteensä"
                                           :summa hoidonjohtopalkkio-summa}
                                          (when indeksit-saatavilla?
                                            {:otsikko "Indeksikorjattu"
                                             :summa (* hoidonjohtopalkkio-summa indeksikerroin)})]
               :summa-johto-ja-hallintokorvaus [{:otsikko "Yhteensä"
                                                 :summa johto-ja-hallintokorvaukset-summa}
                                                (when indeksit-saatavilla?
                                                  {:otsikko "Indeksikorjattu"
                                                   :summa (* johto-ja-hallintokorvaukset-summa indeksikerroin)})]
               :summa-tavoite-ja-kattohinta [{:summa tavoitehinta-summa
                                              :otsikko "Tavoitehinta alkuperäinen"}
                                             (when indeksit-saatavilla?
                                               {:summa (* tavoitehinta-summa indeksikerroin)
                                                :otsikko "Tavoitehinta indeksikorjattu"})
                                             (when (and tavoitehinta-oikaistu
                                                     (not= tavoitehinta-oikaistu tavoitehinta-indeksikorjattu))
                                               {:summa tavoitehinta-oikaistu
                                                :otsikko "Tavoitehinta oikaistu"})
                                             {:summa kattohinta-summa
                                              :otsikko "Kattohinta alkuperäinen"}
                                             (when indeksit-saatavilla?
                                               {:summa (* kattohinta-summa indeksikerroin)
                                                :otsikko "Kattohinta indeksikorjattu"})
                                             (when (and kattohinta-oikaistu
                                                     (not= kattohinta-oikaistu kattohinta-indeksikorjattu))
                                               {:summa kattohinta-oikaistu
                                                :otsikko "Kattohinta oikaistu"})]
               :summa-tilaajan-rahavaraukset [{:otsikko "Yhteensä"
                                               ;; Tälle osiolle ei lasketa indeksikorjauksia, joten näytetään pelkkä summa.
                                               :summa tilaajan-rahavaraukset-summa}]})]
        [navigointivalikko
         avaimet
         hoitokausi
         {:urakka (-> @tila/yleiset :urakka :nimi)
          :soluja (count summa-tavoite-ja-kattohinta)
          :indeksit-saatavilla? indeksit-saatavilla?}
         {::t/hankintakustannukset {:nimi "Hankintakustannukset"
                                    :summat summa-hankinnat
                                    :suunnitelma-vahvistettu? hankintakustannukset-vahvistettu?}
          ::t/erillishankinnat {:nimi "Erillishankinnat"
                                :summat summa-erillishankinnat
                                :suunnitelma-vahvistettu? erillishankinnat-vahvistettu?}
          ::t/johto-ja-hallintokorvaukset {:nimi "Johto- ja hallintokorvaus"
                                           :summat summa-johto-ja-hallintokorvaus
                                           :suunnitelma-vahvistettu? johto-ja-hallintokorvaus-vahvistettu?}
          ::t/hoidonjohtopalkkio {:nimi "Hoidonjohtopalkkio"
                                  :summat summa-hoidonjohtopalkkio
                                  :suunnitelma-vahvistettu? hoidonjohtopalkkio-vahvistettu?}
          ::t/tavoite-ja-kattohinta {:nimi "Tavoite- ja kattohinta"
                                     :suunnitelma-vahvistettu? tavoite-ja-kattohinta-vahvistettu?
                                     :summat summa-tavoite-ja-kattohinta}
          ::t/tilaajan-rahavaraukset {:nimi "Tavoitehinnan ulkopuoliset rahavaraukset"
                                      :summat summa-tilaajan-rahavaraukset
                                      ;; Tilaajan rahavarauksia ei tarvitse vahvistaa, koska sille ei lasketa indeksikorjauksia.
                                      :nayta-osion-status? false}}])
      [yleiset/ajax-loader "Haetaan tietoja"])))

(defn- osionavigointi
  "Kontrollit navigointiin kustannussuunnitelman osien välillä ylös / alas / takaisin sivun alkuun."
  [{:keys [avaimet nykyinen]}]
  (loop [edellinen nil
         jaljella avaimet]
    (if (or (= nykyinen (first jaljella))
          (nil? (first jaljella)))
      [:div.navigointirivi
       [:div.ylos
        [napit/kotiin "Takaisin alkuun" (vieritys/vierita-ylos)]]
       [:div.edellinen-seuraava
        (when edellinen [napit/ylos "Edellinen osio" (vieritys/vierita edellinen)])
        (when (second jaljella) [napit/alas "Seuraava osio" (vieritys/vierita (second jaljella))])]
       [:div.piiloon ; tämä on semmoinen hack että elementit tasoittuu oikein, ihan puhtaasti
        [napit/kotiin "Tää on puhdas hack" (vieritys/vierita-ylos)]]]
      (recur (first jaljella)
        (rest jaljella)))))


;; -- Osion vahvistus --



;; --  Kustannussuunnitelma view ---

(defonce ^{:doc "Jos vaihdellaan tabeja kustannussuunnitelmasta jonnekkin muualle
                 nopeasti, niin async taulukkojen luonti voi aiheuttaa ongelmia.
                 Tämän avulla tarkastetaan, että taulukkojen tila on ok."}
  lopeta-taulukkojen-luonti? (cljs.core/atom false))

(def gridien-polut
  "Gridien polut näkymän tilassa. Näitä käytetään gridien piirtämisessä.
   Yksittäisellä kustannussuunnitelman osiolla voi olla tarve päästä käsiksi usean gridin tilaan."
  [
   ;; Hankintakustannukset osio
   [:gridit :suunnitellut-hankinnat :grid]
   [:gridit :laskutukseen-perustuvat-hankinnat :grid]
   [:gridit :rahavaraukset :grid]

   ;; Erillishankinnat osio
   [:gridit :erillishankinnat :grid]

   ;; Johto- ja hallintokorvaukset osio
   [:gridit :johto-ja-hallintokorvaukset-yhteenveto :grid]
   [:gridit :johto-ja-hallintokorvaukset :grid]
   [:gridit :toimistokulut :grid]

   ;; Hoidonjohtopalkkio osio
   [:gridit :hoidonjohtopalkkio :grid]

   ;; (Tavoite- ja kattohinta osion grid toimii eri tavalla.)

   ;; Tilaajan varaukset osio (HOX, gridi nimin on edelleen "tilaajan-varaukset" vaikka osio on "tilaajan rahavaraukset"!)
   [:gridit :tilaajan-varaukset :grid]])

(defn kustannussuunnitelma*
  [_ app]
  (let [nakyman-setup (cljs.core/atom {:lahdetty-nakymasta? false})]
    (komp/luo
      ;; Alusta tila
      (komp/piirretty
        (fn [_]
          (swap! nakyman-setup
            (fn [{:keys [lahdetty-nakymasta?] :as setup}]
              (assoc setup
                :tilan-alustus
                (go-loop [siivotaan-edellista-taulukkoryhmaa? @lopeta-taulukkojen-luonti?]
                  (cond
                    lahdetty-nakymasta? nil
                    siivotaan-edellista-taulukkoryhmaa? (do (<! (async/timeout 500))
                                                            (recur @lopeta-taulukkojen-luonti?))
                    :else (do
                            (log "[kustannussuunnitelma] TILAN ALUSTUS")
                            (swap! tila/suunnittelu-kustannussuunnitelma (fn [_] tila/kustannussuunnitelma-default))
                            ;; Kutsutaan tilan alustavat Tuck-eventit
                            (loop [[event & events] [(t/->Hoitokausi)
                                                     (t/->TaulukoidenVakioarvot)
                                                     (t/->FiltereidenAloitusarvot)
                                                     (t/->YleisSuodatinArvot)
                                                     (t/->Oikeudet)
                                                     (t/->HaeKustannussuunnitelma)
                                                     (t/->HaeKustannussuunnitelmanTilat)
                                                     (t/->HaeBudjettitavoite)]]
                              (when (and (not (:lahdetty-nakymasta? @nakyman-setup))
                                      (not (nil? event)))
                                (e! event)
                                (recur events)))


                            ;; Luo/päivittää taulukko-gridit ja tallentaa ne tilaan esim. [:gridit :suunnitelmien-tila :grid]
                            ;;  jos ne voi myöhemmin hakea piirrettäväksi grid/piirra!-funktiolla.
                            ;; tf sisältää: [taulukko-f paivita-raidat? tapahtumien-tunnisteet]
                            (loop [[tf & tfs]
                                   ;; Hankintakustannukset osio
                                   [[hankintakustannukset-osio/suunnitellut-hankinnat-grid true nil]
                                    [hankintakustannukset-osio/hankinnat-laskutukseen-perustuen-grid true nil]
                                    [hankintakustannukset-osio/rahavarausten-grid false nil]

                                    ;; Erillishankinnat osio
                                    [erillishankinnat-osio/erillishankinnat-grid true #{:erillishankinnat-disablerivit}]

                                    ;; Johto- ja hallintokorvaukset osio
                                    [johto-ja-hallintokorvaus-osio/johto-ja-hallintokorvaus-laskulla-grid
                                     true
                                     (reduce (fn [tapahtumien-tunnisteet jarjestysnumero]
                                               (let [nimi (t/jh-omienrivien-nimi jarjestysnumero)]
                                                 (conj tapahtumien-tunnisteet (keyword "piilota-itsetaytettyja-riveja-" nimi))))
                                       #{}
                                       (range 1 (inc t/jh-korvausten-omiariveja-lkm)))]
                                    [johto-ja-hallintokorvaus-osio/johto-ja-hallintokorvaus-laskulla-yhteenveto-grid true nil]
                                    [johto-ja-hallintokorvaus-osio/toimistokulut-grid true #{:toimistokulut-disablerivit}]

                                    ;; Hoidonjohtopalkkio osio
                                    [hoidonjohtopalkkio-osio/hoidonjohtopalkkio-grid true #{:hoidonjohtopalkkio-disablerivit}]

                                    ;; Tilaajan rahavaraukset osio
                                    [tilaajan-rahavaraukset-osio/tilaajan-varaukset-grid true #{:tilaajan-varaukset-disablerivit}]]

                                   lahdetty-nakymasta? (:lahdetty-nakymasta? @nakyman-setup)]

                              (when (and (not lahdetty-nakymasta?)
                                      (not (nil? tf)))
                                (let [[taulukko-f paivita-raidat? tapahtumien-tunnisteet] tf
                                      taulukko (taulukko-f)]
                                  (when paivita-raidat?
                                    (t/paivita-raidat! (grid/osa-polusta taulukko [::g-pohjat/data])))
                                  (when tapahtumien-tunnisteet
                                    (doseq [tapahtuma-tunniste tapahtumien-tunnisteet]
                                      (grid/triggeroi-tapahtuma! taulukko tapahtuma-tunniste)))
                                  (recur tfs
                                    (:lahdetty-nakymasta? @nakyman-setup)))))))))))))

      ;; Siivoa näkymä
      (komp/ulos
        (fn []
          (swap! nakyman-setup assoc :lahdetty-nakymasta? true)
          (swap! tila/suunnittelu-kustannussuunnitelma assoc :gridit-vanhentuneet? true)
          #_(log "[kustannussuunnitelma] Yritetään siivota gridit, tila nyt: \n"
              (str "----- Tilassa olevat gridit: ---- \n" (pr-str (sort (keys (filter (comp :grid second) (:gridit @tila/suunnittelu-kustannussuunnitelma))))) "\n")
              (str " ---- Tilasta siivottavat gridit: ---- \n" (pr-str
                                                                 (sort (map second (filter #(get-in @tila/suunnittelu-kustannussuunnitelma %) gridien-polut))))))

          (when-not (some true? (for [grid-polku gridien-polut]
                                  (nil? (get-in @tila/suunnittelu-kustannussuunnitelma grid-polku))))
            (reset! lopeta-taulukkojen-luonti? true)

            (go (<! (:tilan-alustus @nakyman-setup))
              ;; Gridien siivous
              (doseq [grid-polku gridien-polut]
                (grid/siivoa-grid! (get-in @tila/suunnittelu-kustannussuunnitelma grid-polku)))
              (grid/poista-data-kasittelija! tila/suunnittelu-kustannussuunnitelma)

              (log "[kustannussuunnitelma] SIIVOTTU")
              (reset! lopeta-taulukkojen-luonti? false)))))

      ;; Render
      (fn [e*! {:keys [suodattimet gridit-vanhentuneet?]
                {:keys [muutosten-vahvistus]} :domain
                :as app}]
        (set! e! e*!)
        (r/with-let [indeksit-saatavilla?-fn (fn [app]
                                               (let [alkuvuosi (-> @tila/yleiset :urakka :alkupvm pvm/vuosi)
                                                     hoitovuodet (into {}
                                                                   (map-indexed #(-> [(inc %1) %2])
                                                                     (range alkuvuosi (+ alkuvuosi 5))))]
                                                 (some? (first (filter #(= (:vuosi %)
                                                                          (-> app
                                                                            (get-in [:suodattimet :hoitokauden-numero])
                                                                            hoitovuodet))
                                                                 (get-in app [:domain :indeksit]))))))]

          (if gridit-vanhentuneet?
            [yleiset/ajax-loader]
            ;; -- Intro / kustannussuunnitelma-tabin selostus
            [:div#kustannussuunnitelma
             ;; Disabloi kustannussuunnitelman kentät CSS:llä, jos käyttäjällä ei ole kirjoitusoikeutta.
             ;; Kenttään on silti mahdollista siirtyä tabulaattorilla.
             ;; Parempi ratkaisu olisi lisätä disabled-attribuutti HTML:ään.
             {:class (when-not (get-in app [:domain :kirjoitusoikeus?])
                       "vain-luku")}
             [:div [:p "Suunnitelluista kustannuksista muodostetaan summa Sampon kustannussuunnitelmaa varten.
              Kustannussuunnitelmaa voi tarkentaa hoitovuoden kuluessa."]
              [:p "Hallinnollisiin toimenpiteisiin suunnitellut kustannukset siirtyvät kuukauden viimeisenä päivänä
              kuluna Sampon maksuerään." [:br]
               "Muut kulut urakoitsija syöttää Kulut-osiossa. Ne lasketaan mukaan maksueriin eräpäivän mukaan."]
              [:p "Sampoon lähetettävien kustannussuunnitelmasummien ja maksuerien tiedot löydät Kulut > Maksuerät-sivulta. "
               [:br]]]

             (when (< (count @urakka/urakan-toimenpideinstanssit) 7)
               [yleiset/virheviesti-sailio
                (str "Urakasta puuttuu toimenpideinstansseja, jotka täytyy siirtää urakkaan Samposta. "
                  "Toimenpideinstansseja on urakassa nyt "
                  (count @urakka/urakan-toimenpideinstanssit) " kun niitä tarvitaan 7.")])

             ;; -- Kustannussuunnitelman päämenu, jonka linkkejä klikkaamalla vieretetään näkymä liittyvään osioon.
             (let [osioiden-tilat (get-in app [:domain :osioiden-tilat])
                   hoitovuosi-nro (get-in app [:suodattimet :hoitokauden-numero])
                   indeksit-saatavilla? (indeksit-saatavilla?-fn app)]
               (vieritys/vieritettava-osio
                 {:osionavigointikomponentti osionavigointi
                  :menukomponentti menukomponentti
                  :parametrit {:menu {:app app
                                      :indeksit-saatavilla? indeksit-saatavilla?}
                               :navigointi {:indeksit-saatavilla? indeksit-saatavilla?}}}

                 ;; Osiot
                 ::t/hankintakustannukset
                 [debug/debug (get-in app [:domain :osioiden-tilat])]
                 [hankintakustannukset-osio/osio
                  (ks-yhteiset/osio-vahvistettu? osioiden-tilat :hankintakustannukset hoitovuosi-nro)
                  (get-in app [:domain :kirjoitusoikeus?])
                  (get-in app [:domain :indeksit])
                  (get-in app [:domain :kuluva-hoitokausi])
                  (get-in app [:gridit :suunnitellut-hankinnat :grid])
                  (get-in app [:gridit :laskutukseen-perustuvat-hankinnat :grid])
                  (get-in app [:gridit :rahavaraukset :grid])
                  (get-in app [:yhteenvedot :hankintakustannukset])
                  (:kantahaku-valmis? app)
                  suodattimet]
                 [osion-vahvistus/vahvista-osio-komponentti :hankintakustannukset
                  {:osioiden-tilat (get-in app [:domain :osioiden-tilat])
                   :hoitovuosi-nro hoitovuosi-nro
                   :indeksit-saatavilla? indeksit-saatavilla?}]

                 ::t/erillishankinnat
                 [erillishankinnat-osio/osio
                  (ks-yhteiset/osio-vahvistettu? osioiden-tilat :erillishankinnat hoitovuosi-nro)
                  (get-in app [:domain :indeksit])
                  (get-in app [:gridit :erillishankinnat :grid])
                  (get-in app [:yhteenvedot :johto-ja-hallintokorvaukset :summat :erillishankinnat])
                  (get-in app [:yhteenvedot :johto-ja-hallintokorvaukset :indeksikorjatut-summat :erillishankinnat])
                  (:kantahaku-valmis? app)
                  (dissoc suodattimet :hankinnat)
                  (get-in app [:domain :kuluva-hoitokausi])]
                 [osion-vahvistus/vahvista-osio-komponentti :erillishankinnat
                  {:osioiden-tilat osioiden-tilat
                   :hoitovuosi-nro hoitovuosi-nro
                   :indeksit-saatavilla? indeksit-saatavilla?}]

                 ::t/johto-ja-hallintokorvaukset
                 [johto-ja-hallintokorvaus-osio/osio
                  (ks-yhteiset/osio-vahvistettu? osioiden-tilat :johto-ja-hallintokorvaus hoitovuosi-nro)
                  (get-in app [:gridit :johto-ja-hallintokorvaukset :grid])
                  (get-in app [:gridit :johto-ja-hallintokorvaukset-yhteenveto :grid])
                  (get-in app [:gridit :toimistokulut :grid])
                  (dissoc suodattimet :hankinnat)
                  (get-in app [:yhteenvedot :johto-ja-hallintokorvaukset :summat :johto-ja-hallintokorvaukset])
                  (get-in app [:yhteenvedot :johto-ja-hallintokorvaukset :summat :toimistokulut])
                  (get-in app [:yhteenvedot :johto-ja-hallintokorvaukset :indeksikorjatut-summat :johto-ja-hallintokorvaukset])
                  (get-in app [:yhteenvedot :johto-ja-hallintokorvaukset :indeksikorjatut-summat :toimistokulut])
                  (get-in app [:domain :kuluva-hoitokausi])
                  (get-in app [:domain :indeksit])
                  (:kantahaku-valmis? app)]
                 [osion-vahvistus/vahvista-osio-komponentti :johto-ja-hallintokorvaus
                  {:osioiden-tilat osioiden-tilat
                   :hoitovuosi-nro hoitovuosi-nro
                   :indeksit-saatavilla? indeksit-saatavilla?}]

                 ::t/hoidonjohtopalkkio
                 [hoidonjohtopalkkio-osio/osio
                  (ks-yhteiset/osio-vahvistettu? osioiden-tilat :hoidonjohtopalkkio hoitovuosi-nro)
                  (get-in app [:gridit :hoidonjohtopalkkio :grid])
                  (get-in app [:yhteenvedot :johto-ja-hallintokorvaukset :summat :hoidonjohtopalkkio])
                  (get-in app [:yhteenvedot :johto-ja-hallintokorvaukset :indeksikorjatut-summat :hoidonjohtopalkkio])
                  (get-in app [:domain :indeksit])
                  (get-in app [:domain :kuluva-hoitokausi])
                  (dissoc suodattimet :hankinnat)
                  (:kantahaku-valmis? app)]
                 [osion-vahvistus/vahvista-osio-komponentti :hoidonjohtopalkkio
                  {:osioiden-tilat osioiden-tilat
                   :hoitovuosi-nro hoitovuosi-nro
                   :indeksit-saatavilla? indeksit-saatavilla?}]

                 ::t/tavoite-ja-kattohinta
                 [tavoite-ja-kattohinta-osio/osio
                  e!
                  (ks-yhteiset/osio-vahvistettu? osioiden-tilat :tavoite-ja-kattohinta hoitovuosi-nro)
                  (:urakka @tila/yleiset)
                  (get app :yhteenvedot)
                  (get-in app [:domain :kuluva-hoitokausi])
                  (get-in app [:domain :indeksit])
                  (select-keys suodattimet [:hoitokauden-numero])
                  (:kantahaku-valmis? app)]
                 [osion-vahvistus/vahvista-osio-komponentti :tavoite-ja-kattohinta
                  {:vahvistus-vaadittu-osiot (:vahvistus-vaadittu-osiot
                                               (mhu-domain/osioiden-riippuvuudet :tavoite-ja-kattohinta))
                   :osioiden-tilat osioiden-tilat
                   :hoitovuosi-nro hoitovuosi-nro
                   :indeksit-saatavilla? indeksit-saatavilla?
                   :osiossa-virheita? (some? (get-in app [:kattohinta :virheet 0 (keyword (str "kattohinta-vuosi-" hoitovuosi-nro))]))}]

                 ::t/tilaajan-rahavaraukset
                 [tilaajan-rahavaraukset-osio/osio
                  ;; HOX, gridin nimi on edelleen "tilaajan-varaukset" vaikka osio on "tilaajan rahavaraukset"!
                  (get-in app [:gridit :tilaajan-varaukset :grid])
                  (dissoc suodattimet :hankinnat)
                  (:kantahaku-valmis? app)]
                 ;; Tälle osiolle ei tehdä vahvistusta, koska tilaajan rahavarauksille ei lasketa indeksikorjauksia.
                 ))

             ;; Näytä vahvistusdialogi, jos vaaditaan muutosten vahvistus.
             (let [{:keys [vaaditaan-muutosten-vahvistus? muutos-vahvistettu-fn]} muutosten-vahvistus]
               (when vaaditaan-muutosten-vahvistus?
                 [osion-vahvistus/muutokset-estetty-modal #_osion-vahvistus/muutosten-vahvistus-modal
                  muutos-vahvistettu-fn
                  (r/partial
                    (fn [hoitovuosi polku e]
                      (let [arvo (.. e -target -value)
                            numero? (-> arvo js/Number js/isNaN not)
                            arvo (if numero?
                                   (js/Number arvo)
                                   arvo)]
                        (e! (tuck-apurit/->MuutaTila [:domain :muutosten-vahvistus :tiedot hoitovuosi polku] arvo)))))
                  (get-in app [:domain :muutosten-vahvistus])]))]))))))


;; View-komponentti
(defn kustannussuunnitelma
  "Kustannussuunnitelma välilehti"
  []
  [tuck/tuck tila/suunnittelu-kustannussuunnitelma kustannussuunnitelma*])

(defn ^:export hae-kustannussuunnitelman-data []
  (e! (t/->HaeKustannussuunnitelma)))

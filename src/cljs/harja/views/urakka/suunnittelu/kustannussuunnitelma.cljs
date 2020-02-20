(ns harja.views.urakka.suunnittelu.kustannussuunnitelma
  (:require [reagent.core :as r :refer [atom]]
            [clojure.string :as clj-str]
            [cljs.core.async :as async :refer [<! >! chan timeout]]
            [clojure.set :as clj-set]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.suunnittelu.mhu-kustannussuunnitelma :as t]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.debug :as debug]
            [harja.ui.taulukko.grid-osan-vaihtaminen :as gov]
            [harja.ui.taulukko.grid-pohjat :as g-pohjat]
            [harja.ui.taulukko.grid :as grid]
            [harja.ui.taulukko.impl.solu :as solu]
            [harja.ui.taulukko.impl.alue :as alue]
            [harja.ui.taulukko.grid-oletusarvoja :as konf]
            [harja.ui.taulukko.taulukko :as taulukko]
            [harja.ui.taulukko.jana :as jana]
            [harja.ui.taulukko.protokollat :as p]
            [harja.ui.taulukko.osa :as osa]
            [harja.ui.taulukko.tyokalut :as tyokalut]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :as yleiset]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [goog.dom :as dom])
  (:require-macros [harja.ui.taulukko.tyokalut :refer [muodosta-taulukko]]
                   [harja.ui.taulukko.grid :refer [defsolu]]
                   [cljs.core.async.macros :refer [go go-loop]]))


(defonce e! nil)

(declare hoidonjohtopalkkio-grid)

(defn summa-formatointi [teksti]
  (let [teksti (clj-str/replace (str teksti) "," ".")]
    (if (or (= "" teksti) (js/isNaN teksti))
      "0,00"
      (fmt/desimaaliluku teksti 2 true))))

(defn summa-formatointi-uusi [teksti]
  (if (nil? teksti)
    ""
    (let [teksti (clj-str/replace (str teksti) "," ".")]
      (if (or (= "" teksti) (js/isNaN teksti))
        "0,00"
        (fmt/desimaaliluku teksti 2 true)))))

(defn summa-formatointi-aktiivinen [teksti]
  (let [teksti-ilman-pilkkua (clj-str/replace (str teksti) "," ".")]
    (cond
      (or (nil? teksti) (= "" teksti)) ""
      (= t/vaihtelua-teksti teksti) t/vaihtelua-teksti
      (re-matches #".*\.0*$" teksti-ilman-pilkkua) (apply str (fmt/desimaaliluku teksti-ilman-pilkkua nil true)
                                                          (drop 1 (re-find #".*(\.|,)(0*)" teksti)))
      :else (fmt/desimaaliluku teksti-ilman-pilkkua nil true))))

(defn yhteenveto-format [teksti]
  (let [teksti (if (or (= "" teksti) (.isNaN js/Number teksti))
                 "0,00"
                 (str teksti))
        oletettu-numero (clj-str/replace teksti "," ".")
        numero? (and (not (js/isNaN (js/Number oletettu-numero)))
                     (not= "" (clj-str/trim teksti)))
        teksti (if numero?
                 oletettu-numero
                 teksti)]
    (if numero?
      (fmt/desimaaliluku teksti 2 true)
      teksti)))

(defn poista-tyhjat [arvo]
  (clj-str/replace arvo #"\s" ""))

;;;;; TAULUKOT ;;;;;;;
(defsolu VaylaCheckbox
  [vaihda-fn txt]
  {:pre [(fn? vaihda-fn)]}
  (fn suunnittele-kuukausitasolla-filter [this]
    (let [taman-data (solu/taman-derefable this)
          kuukausitasolla? @taman-data
          osan-id (str (grid/hae-osa this :id))]
      [:div
       [:input.vayla-checkbox {:id osan-id
                               :type "checkbox" :checked kuukausitasolla?
                               :on-change (partial (:vaihda-fn this) this)}]
       [:label {:for osan-id} (:txt this)]])))

(defonce ^{:mutable true
           :doc "Hieman likaisen oloista, mutta tätä käytetään sen takia, että SyoteTaytaAlas komponentitn on-blur eventissä
 tarvitsee event arvoa, jotta nähdään, että onko nappia painettu. Halutaan
 kummiskin antaa nappia-painettu! funktiolle eventin sijasta se arvo, joka tulle
 käyttäytymisistä."}
         rivit-alla_ nil)
(defonce ^{:mutable true
          :doc "Jos halutaan estää blur event focus eventin jälkeen, niin tätä voi käyttää"}
         esta-blur_ false)

;"Käytännössä input kenttä, mutta sillä lisäominaisuudella, että fokusoituna, tulee
;     'Täytä alas' nappi päälle."
(defsolu SyoteTaytaAlas
  [nappi-nakyvilla? nappia-painettu! toiminnot kayttaytymiset parametrit fmt fmt-aktiivinen]
  {:pre [(boolean? nappi-nakyvilla?)]}
  (fn hankintasuunnitelmien-syotto
    [this]
    (let [on-change (fn [arvo]
                      #_(when arvo
                          (e! (t/->PaivitaTaulukonOsa (::tama-komponentti osa/*this*) polku-taulukkoon
                                                      (fn [komponentin-tila]
                                                        (assoc komponentin-tila :value arvo))))))
          on-blur (fn [event]
                    (let [klikattu-elementti (.-relatedTarget event)
                          klikattu-nappia? (and (not (nil? klikattu-elementti))
                                                (or (.getAttribute klikattu-elementti "data-kopioi-allaoleviin")
                                                    (= (str (.getAttribute klikattu-elementti "data-id"))
                                                       (str (grid/hae-osa (::tama-komponentti solu/*this*) :id)))))
                          {osan-paikka :osan-paikka} (grid/solun-asia (::tama-komponentti solu/*this*) :tunniste-rajapinnan-dataan)
                          rivin-paikka (first osan-paikka)
                          rivit-alla (when klikattu-nappia?
                                       (grid/gridin-rivit (grid/osa-polusta (::tama-komponentti solu/*this*) [:.. :..])
                                                          (fn [osa]
                                                            (and (instance? alue/Rivi osa)
                                                                 (> (last (grid/osien-yhteinen-asia osa :index-polku))
                                                                    rivin-paikka)))))]
                      (when rivit-alla
                        (set! rivit-alla_ rivit-alla))
                      event
                      #_(e! (t/->PaivitaToimenpideTaulukko (::tama-komponentti osa/*this*) polku-taulukkoon))
                      #_(e! (t/->PaivitaKustannussuunnitelmanYhteenvedot))
                      ;; Tarkastetaan onko klikattu elementti nappi tai tämä elementti itsessään. Jos on, ei piilloteta nappia.
                      #_(when-not klikattu-nappia?
                          (e! (t/->TallennaHankintasuunnitelma toimenpide-avain (::tama-komponentti osa/*this*) polku-taulukkoon false laskutuksen-perusteella-taulukko?))
                          (e! (t/->PaivitaTaulukonOsa (::tama-komponentti osa/*this*) polku-taulukkoon
                                                      (fn [komponentin-tila]
                                                        (assoc komponentin-tila :nappi-nakyvilla? false))))
                          (go (>! kaskytyskanava [:tavoite-ja-kattohinta (t/->TallennaJaPaivitaTavoiteSekaKattohinta)])))))
          on-focus (fn [_]
                     #_(e! (t/->PaivitaTaulukonOsa (::tama-komponentti osa/*this*) polku-taulukkoon
                                                   (fn [komponentin-tila]
                                                     (assoc komponentin-tila :nappi-nakyvilla? true)))))
          on-key-down (fn [event]
                        #_(when (= "Enter" (.. event -key))
                            (.. event -target blur)))
          input-osa (solu/syote {:toiminnot (into {}
                                                  (map (fn [[k f]]
                                                         [k (fn [x]
                                                              (binding [solu/*this* (::tama-komponentti solu/*this*)]
                                                                (if (= k :on-blur)
                                                                  (do (when rivit-alla_
                                                                        (nappia-painettu! rivit-alla_ x)
                                                                        (set! rivit-alla_ nil))
                                                                      (f x))
                                                                  (f x))))])
                                                       toiminnot))
                                 :kayttaytymiset (into {}
                                                       (map (fn [[k v]]
                                                              [k (if (= k :on-blur)
                                                                   (conj (or v []) {:oma {:f on-blur}})
                                                                   v)])
                                                            kayttaytymiset))
                                 :parametrit parametrit
                                 :fmt fmt
                                 :fmt-aktiivinen fmt-aktiivinen}
                                #_{:toiminnot {:on-change on-change
                                               :on-blur on-blur
                                               :on-focus (fn [event]
                                                           (binding [solu/*this* this]
                                                             (on-focus event)))
                                               :on-key-down on-key-down}
                                   :kayttaytymiset {:on-change [{:positiivinen-numero {:desimaalien-maara 2}}
                                                                {:eventin-arvo {:f poista-tyhjat}}]}
                                   :parametrit parametrit
                                   :fmt summa-formatointi
                                   :fmt-aktiivinen summa-formatointi-aktiivinen})
          #_(assoc (solu/syote {:toiminnot (into {}
                                                         (map (fn [[k f]]
                                                                [k (fn [x]
                                                                     (binding [solu/*this* (::tama-komponentti solu/*this*)]
                                                                       (if (= k :on-blur)
                                                                         (do (when rivit-alla_
                                                                               (nappia-painettu! rivit-alla_ x)
                                                                               (set! rivit-alla_ nil))
                                                                             (f x))
                                                                         (f x))))])
                                                              toiminnot))
                                        :kayttaytymiset (into {}
                                                              (map (fn [[k v]]
                                                                     [k (if (= k :on-blur)
                                                                          (conj (or v []) {:oma {:f on-blur}})
                                                                          v)])
                                                                   kayttaytymiset))
                                        :parametrit parametrit
                                        :fmt fmt
                                        :fmt-aktiivinen fmt-aktiiviselle}
                                       #_{:toiminnot {:on-change on-change
                                                      :on-blur on-blur
                                                      :on-focus (fn [event]
                                                                  (binding [solu/*this* this]
                                                                    (on-focus event)))
                                                      :on-key-down on-key-down}
                                          :kayttaytymiset {:on-change [{:positiivinen-numero {:desimaalien-maara 2}}
                                                                       {:eventin-arvo {:f poista-tyhjat}}]}
                                          :parametrit parametrit
                                          :fmt summa-formatointi
                                          :fmt-aktiivinen summa-formatointi-aktiivinen})
                           :harja.ui.taulukko.impl.grid/osan-derefable (grid/solun-asia this :osan-derefable))
          #_(-> (solu/->Syote (keyword (str nimi "-maara-kk"))

                              )
                (p/lisaa-fmt summa-formatointi)
                (p/lisaa-fmt-aktiiviselle summa-formatointi-aktiivinen))
          tayta-alas! (fn [this _]
                        #_(e! (t/->PaivitaTaulukonOsa this polku-taulukkoon
                                                      (fn [komponentin-tila]
                                                        (assoc komponentin-tila :nappi-nakyvilla? false))))
                        #_(e! (t/->TaytaAlas this polku-taulukkoon))
                        #_(e! (t/->TallennaHankintasuunnitelma toimenpide-avain this polku-taulukkoon true laskutuksen-perusteella-taulukko?))
                        #_(go (>! kaskytyskanava [:tavoite-ja-kattohinta (t/->TallennaJaPaivitaTavoiteSekaKattohinta)])))]
      (fn [this]
        [:div.kustannus-syotto {#_#_:class (apply str (interpose " " luokat))
                                :tab-index -1
                                :data-id (str (grid/hae-osa this :id))}
         [napit/yleinen-ensisijainen "Kopioi allaoleviin" (r/partial tayta-alas! this)
          {:luokka (str "kopioi-nappi button-primary-default "
                        (when-not (:nappi-nakyvilla? this)
                          "piillotettu"))
           :data-attributes {:data-kopioi-allaoleviin true}
           :tabindex 0}]
         [grid/piirra (assoc input-osa ::tama-komponentti this
                             :harja.ui.taulukko.impl.grid/osan-derefable (grid/solun-asia this :osan-derefable)) #_(-> input-osa
                                                                      (p/aseta-arvo :arvo value)
                                                                      (assoc ::tama-komponentti this))]]))))

(defn maara-solujen-disable! [data-sisalto disabled?]
  (grid/post-walk-grid! data-sisalto
                        (fn [osa]
                          (when (instance? solu/Syote osa)
                            (grid/paivita-osa! osa
                                               (fn [solu]
                                                 (assoc-in solu [:parametrit :disabled?] disabled?)))))))


(defn rivi->rivi-kuukausifiltterilla [pohja? rivi]
  (let [sarakkeiden-maara (count (grid/hae-grid rivi :lapset))]
    (grid/grid {:alueet [{:sarakkeet [0 1] :rivit [0 2]}]
                :koko (assoc-in konf/auto
                                [:rivi :nimet]
                                {::t/yhteenveto 0
                                 ::t/valinta 1})
                :osat [(grid/paivita-grid! (grid/aseta-nimi rivi ::t/yhteenveto)
                                           :lapset
                                           (fn [osat]
                                             (mapv (fn [osa]
                                                     (if (instance? solu/Laajenna osa)
                                                       (assoc osa :auki-alussa? true)
                                                       osa))
                                                   osat)))
                       (grid/rivi {:osat (vec
                                           (cons (vayla-checkbox (fn [this event]
                                                                   (.preventDefault event)
                                                                   (let [kuukausitasolla? (not (grid/arvo-rajapinnasta (grid/osien-yhteinen-asia this :datan-kasittelija)
                                                                                                                       :kuukausitasolla?))]
                                                                     (e! (tuck-apurit/->MuutaTila [:gridit :hoidonjohtopalkkio :kuukausitasolla?] kuukausitasolla?))))
                                                                 "Haluan suunnitella jokaiselle kuukaudelle määrän erikseen")
                                                 (repeatedly (dec sarakkeiden-maara) (fn [] (solu/tyhja)))))
                                   :koko {:seuraa {:seurattava (if pohja?
                                                                 ::g-pohjat/otsikko
                                                                 ::otsikko)
                                                   :sarakkeet :sama
                                                   :rivit :sama}}
                                   :nimi ::t/valinta}
                                  [{:sarakkeet [0 sarakkeiden-maara] :rivit [0 1]}])]})))

(defn rivi-kuukausifiltterilla->rivi [pohja? rivi-kuukausifiltterilla]
  (grid/aseta-nimi (grid/paivita-grid! (grid/get-in-grid rivi-kuukausifiltterilla [::t/yhteenveto])
                                       :lapset
                                       (fn [osat]
                                         (mapv (fn [osa]
                                                 (if (instance? solu/Laajenna osa)
                                                   (assoc osa :auki-alussa? false)
                                                   osa))
                                               osat)))
                   (if pohja?
                     ::g-pohjat/data-yhteenveto
                     ::data-yhteenveto)))

(defn rivi-kuukausifiltterilla!
  [laajennasolu pohja? & datan-kasittely]
  (apply grid/vaihda-osa!
         (-> laajennasolu grid/vanhempi)
         (partial rivi->rivi-kuukausifiltterilla pohja?)
         datan-kasittely))

(defn rivi-ilman-kuukausifiltteria!
  [laajennasolu pohja? & datan-kasittely]
  (apply grid/vaihda-osa!
         (-> laajennasolu grid/vanhempi grid/vanhempi)
         (partial rivi-kuukausifiltterilla->rivi pohja?)
         datan-kasittely))


(defn tyhja->syote-tayta-alas [tyhja {:keys [nappi-nakyvilla? nappia-painettu! toiminnot kayttaytymiset parametrit fmt fmt-aktiivinen]}]
  (-> (->SyoteTaytaAlas (grid/hae-osa tyhja :id) nappi-nakyvilla? (or nappia-painettu! identity) toiminnot kayttaytymiset parametrit fmt fmt-aktiivinen)
      (merge (dissoc tyhja :id))))

(defn hankintojen-pohja [taulukon-id
                         nappia-painettu!
                         on-change
                         on-blur]
  (let [nyt (pvm/nyt)
        g (g-pohjat/grid-pohjasta g-pohjat/grid-pohja-4)]
    (grid/paivita-grid! (grid/get-in-grid g [::g-pohjat/otsikko])
                        :lapset
                        (fn [lapset]
                          (vec
                            (map-indexed (fn [index lapsi]
                                           (if (= 3 index)
                                             (gov/tyhja->teksti lapsi {:class #{"table-default" "table-default-header" "harmaa-teksti"}})
                                             (gov/tyhja->teksti lapsi {:class #{"table-default" "table-default-header"}})))
                                         lapset))))
    (grid/paivita-grid! (grid/get-in-grid g [::g-pohjat/data])
                        :lapset
                        (fn [lapset]
                          (let [otsikko-data-pohja (first lapset)]
                            (mapv (fn [hoitokauden-numero]
                                    (let [kopio (grid/samanlainen-osa otsikko-data-pohja)]
                                      (grid/paivita-grid! (grid/get-in-grid kopio [::g-pohjat/data-yhteenveto])
                                                          :lapset
                                                          (fn [lapset]
                                                            [(gov/tyhja->laajenna (get lapset 0)
                                                                                  (fn [this auki?]
                                                                                    (t/laajenna-solua-klikattu this auki? taulukon-id [::g-pohjat/data]))
                                                                                  false
                                                                                  {:class #{"table-default" "lihavoitu"}})
                                                             (gov/tyhja->teksti (get lapset 1) {:class #{"table-default"}})
                                                             (gov/tyhja->teksti (get lapset 2) {:class #{"table-default"}} {:fmt yhteenveto-format})
                                                             (gov/tyhja->teksti (get lapset 3) {:class #{"table-default" "harmaa-teksti"}} {:fmt yhteenveto-format})]))
                                      (grid/paivita-grid! (grid/get-in-grid kopio [::g-pohjat/data-sisalto])
                                                          :lapset
                                                          (fn [lapset]
                                                            (let [rivipohja (first lapset)]
                                                              (vec (repeatedly 12
                                                                               (fn []
                                                                                 (let [rivi (grid/samanlainen-osa rivipohja)]
                                                                                   (grid/paivita-grid! rivi
                                                                                                       :lapset
                                                                                                       (fn [osat]
                                                                                                         [(gov/tyhja->teksti (get osat 0)
                                                                                                                             {:class #{"table-default"}}
                                                                                                                             {:fmt (fn [paivamaara]
                                                                                                                                     (when paivamaara
                                                                                                                                       (let [teksti (-> paivamaara pvm/kuukausi pvm/kuukauden-lyhyt-nimi (str "/" (pvm/vuosi paivamaara)))
                                                                                                                                             mennyt? (and (pvm/ennen? paivamaara nyt)
                                                                                                                                                          (or (not= (pvm/kuukausi nyt) (pvm/kuukausi paivamaara))
                                                                                                                                                              (not= (pvm/vuosi nyt) (pvm/vuosi paivamaara))))]
                                                                                                                                         (if mennyt?
                                                                                                                                           (str teksti " (mennyt)")
                                                                                                                                           teksti))))})
                                                                                                          (tyhja->syote-tayta-alas (get osat 1)
                                                                                                                                   {:nappi-nakyvilla? false
                                                                                                                                    :nappia-painettu! (partial nappia-painettu! hoitokauden-numero)
                                                                                                                                    :toiminnot {:on-change (partial on-change hoitokauden-numero)
                                                                                                                                                :on-focus (fn [_]
                                                                                                                                                            (grid/paivita-osa! solu/*this*
                                                                                                                                                                               (fn [solu]
                                                                                                                                                                                 (assoc solu :nappi-nakyvilla? true))))
                                                                                                                                                :on-blur (partial on-blur hoitokauden-numero)
                                                                                                                                                :on-key-down (fn [event]
                                                                                                                                                               (when (= "Enter" (.. event -key))
                                                                                                                                                                 (.. event -target blur)))}
                                                                                                                                    :kayttaytymiset {:on-change [{:positiivinen-numero {:desimaalien-maara 2}}
                                                                                                                                                                 {:eventin-arvo {:f poista-tyhjat}}]
                                                                                                                                                     :on-blur [:positiivinen-numero {:eventin-arvo {:f poista-tyhjat}}]}
                                                                                                                                    :parametrit {:size 2
                                                                                                                                                 :class #{"input-default"}}
                                                                                                                                    :fmt summa-formatointi-uusi
                                                                                                                                    :fmt-aktiivinen summa-formatointi-aktiivinen})
                                                                                                          (gov/tyhja->teksti (get osat 2) {:class #{"table-default"}} {:fmt summa-formatointi})
                                                                                                          (gov/tyhja->teksti (get osat 3) {:class #{"table-default" "harmaa-teksti"}})])))))))))
                                      kopio))
                                  (range 1 6)))))
    (grid/paivita-grid! (grid/get-in-grid g [::g-pohjat/yhteenveto])
                        :lapset
                        (fn [lapset]
                          [(gov/tyhja->teksti (get lapset 0) {:class #{"table-default" "table-default-sum"}})
                           (gov/tyhja->teksti (get lapset 1) {:class #{"table-default" "table-default-sum"}})
                           (gov/tyhja->teksti (get lapset 2) {:class #{"table-default" "table-default-sum"}} {:fmt yhteenveto-format})
                           (gov/tyhja->teksti (get lapset 3) {:class #{"table-default" "table-default-sum" "harmaa-teksti"}} {:fmt yhteenveto-format})]))
    (grid/paivita-grid! g :koko (fn [koko]
                                  (update koko :rivi (fn [rivi]
                                                       (assoc rivi :korkeudet {0 "40px"
                                                                               2 "40px"})))))
    (grid/paivita-grid! g :parametrit (fn [parametrit] (assoc parametrit :id taulukon-id)))
    g))

(defn maarataulukon-pohja [taulukon-id
                           on-change
                           on-blur
                           on-change-kk
                           on-blur-kk]
  {:pre [(string? taulukon-id)
         (every? fn? #{on-change on-blur on-change-kk on-blur-kk})]}
  (let [nyt (pvm/nyt)
        yhteenveto-grid-rajapinta-asetukset {:rajapinta :yhteenveto
                                             :solun-polun-pituus 1
                                             :jarjestys [[:nimi :maara :yhteensa :indeksikorjattu]]
                                             :datan-kasittely (fn [yhteenveto]
                                                                (mapv (fn [[_ v]]
                                                                        v)
                                                                      yhteenveto))
                                             :tunnisteen-kasittely (fn [osat _]
                                                                     (mapv (fn [osa]
                                                                             (when (instance? solu/Syote osa)
                                                                               :maara))
                                                                           (grid/hae-grid osat :lapset)))}
        g (g-pohjat/grid-pohjasta g-pohjat/grid-pohja-4)]
    (grid/paivita-grid! (grid/get-in-grid g [::g-pohjat/otsikko])
                        :lapset
                        (fn [lapset]
                          (vec
                            (map-indexed (fn [index lapsi]
                                           (if (= 3 index)
                                             (gov/tyhja->teksti lapsi {:class #{"table-default" "table-default-header" "harmaa-teksti"}})
                                             (gov/tyhja->teksti lapsi {:class #{"table-default" "table-default-header"}})))
                                         lapset))))
    (grid/paivita-grid! (grid/get-in-grid g [::g-pohjat/data 0 ::g-pohjat/data-yhteenveto])
                        :lapset
                        (fn [lapset]
                          [(gov/tyhja->laajenna (get lapset 0)
                                                (fn [this auki?]
                                                  (if auki?
                                                    (rivi-kuukausifiltterilla! this
                                                                               true
                                                                               [:. ::t/yhteenveto] yhteenveto-grid-rajapinta-asetukset
                                                                               [:. ::t/valinta] {:rajapinta :kuukausitasolla?
                                                                                                 :solun-polun-pituus 1
                                                                                                 :datan-kasittely (fn [kuukausitasolla?]
                                                                                                                    [kuukausitasolla? nil nil nil])})
                                                    (rivi-ilman-kuukausifiltteria! this
                                                                                   true
                                                                                   [:.. ::g-pohjat/data-yhteenveto] yhteenveto-grid-rajapinta-asetukset))
                                                  (t/laajenna-solua-klikattu this auki? taulukon-id [::g-pohjat/data] {:sulkemis-polku [:.. :.. :.. 1]}))
                                                false
                                                {:class #{"table-default" "lihavoitu"}})
                           (gov/tyhja->syote (get lapset 1)
                                             {:on-change on-change
                                              :on-blur on-blur
                                              :on-key-down (fn [event]
                                                             (when (= "Enter" (.. event -key))
                                                               (.. event -target blur)))}
                                             {:on-change [{:positiivinen-numero {:desimaalien-maara 2}}
                                                          {:eventin-arvo {:f poista-tyhjat}}]
                                              :on-blur [:str->number :numero-pisteella :positiivinen-numero {:eventin-arvo {:f poista-tyhjat}}]}
                                             {:size 2
                                              :class #{"input-default"}}
                                             {:fmt yhteenveto-format
                                              :fmt-aktiivinen summa-formatointi-aktiivinen})
                           (gov/tyhja->teksti (get lapset 2) {:class #{"table-default"}} {:fmt yhteenveto-format})
                           (gov/tyhja->teksti (get lapset 3) {:class #{"table-default" "harmaa-teksti"}} {:fmt yhteenveto-format})]))
    (grid/paivita-grid! (grid/get-in-grid g [::g-pohjat/data 0 ::g-pohjat/data-sisalto])
                        :lapset
                        (fn [lapset]
                          (let [rivipohja (first lapset)]
                            (vec (repeatedly 12
                                             (fn []
                                               (let [rivi (grid/samanlainen-osa rivipohja)]
                                                 (grid/paivita-grid! rivi
                                                                     :lapset
                                                                     (fn [osat]
                                                                       [(gov/tyhja->teksti (get osat 0)
                                                                                           {:class #{"table-default"}}
                                                                                           {:fmt (fn [paivamaara]
                                                                                                   (let [teksti (-> paivamaara pvm/kuukausi pvm/kuukauden-lyhyt-nimi (str "/" (pvm/vuosi paivamaara)))
                                                                                                         mennyt? (and (pvm/ennen? paivamaara nyt)
                                                                                                                      (or (not= (pvm/kuukausi nyt) (pvm/kuukausi paivamaara))
                                                                                                                          (not= (pvm/vuosi nyt) (pvm/vuosi paivamaara))))]
                                                                                                     (if mennyt?
                                                                                                       (str teksti " (mennyt)")
                                                                                                       teksti)))})
                                                                        (gov/tyhja->syote (get osat 1)
                                                                                          {:on-change on-change-kk
                                                                                           :on-blur on-blur-kk
                                                                                           :on-key-down (fn [event]
                                                                                                          (when (= "Enter" (.. event -key))
                                                                                                            (.. event -target blur)))}
                                                                                          {:on-change [{:positiivinen-numero {:desimaalien-maara 2}}
                                                                                                       {:eventin-arvo {:f poista-tyhjat}}]
                                                                                           :on-blur [:positiivinen-numero {:eventin-arvo {:f poista-tyhjat}}]}
                                                                                          {:size 2
                                                                                           :class #{"input-default"}}
                                                                                          {:fmt summa-formatointi-uusi
                                                                                           :fmt-aktiivinen summa-formatointi-aktiivinen})
                                                                        (gov/tyhja->teksti (get osat 2) {:class #{"table-default"}})
                                                                        (gov/tyhja->teksti (get osat 3) {:class #{"table-default" "harmaa-teksti"}})])))))))))
    (grid/paivita-grid! (grid/get-in-grid g [::g-pohjat/yhteenveto])
                        :lapset
                        (fn [lapset]
                          [(gov/tyhja->teksti (get lapset 0) {:class #{"table-default" "table-default-sum"}})
                           (gov/tyhja->teksti (get lapset 1) {:class #{"table-default" "table-default-sum"}})
                           (gov/tyhja->teksti (get lapset 2) {:class #{"table-default" "table-default-sum"}} {:fmt yhteenveto-format})
                           (gov/tyhja->teksti (get lapset 3) {:class #{"table-default" "table-default-sum" "harmaa-teksti"}} {:fmt yhteenveto-format})]))
    (grid/paivita-grid! g :koko (fn [koko]
                                  (update koko :rivi (fn [rivi]
                                                       (assoc rivi :korkeudet {0 "40px"
                                                                               2 "40px"})))))
    (grid/paivita-grid! g :parametrit (fn [parametrit] (assoc parametrit :id taulukon-id)))
    g))


(defn suunnittellut-hankinnat-grid []
  (let [g-pohja (hankintojen-pohja "suunnittellut-hankinnat-taulukko"
                                   (fn [hoitokauden-numero rivit-alla arvo]
                                     (when (and arvo (not (empty? rivit-alla)))
                                       (doseq [rivi rivit-alla
                                               :let [maara-solu (grid/get-in-grid rivi [1])
                                                     piillotettu? (grid/piillotettu? rivi)]]
                                         (when-not piillotettu?
                                           (t/paivita-solun-arvo {:paivitettava-asia :aseta-suunnittellut-hankinnat!
                                                                  :arvo arvo
                                                                  :solu maara-solu}
                                                                 true
                                                                 hoitokauden-numero)))))
                                   (fn [hoitokauden-numero arvo]
                                     (when arvo
                                       (t/paivita-solun-arvo {:paivitettava-asia :aseta-suunnittellut-hankinnat!
                                                              :arvo arvo
                                                              :solu solu/*this*}
                                                             false
                                                             hoitokauden-numero)))
                                   (fn [hoitokauden-numero arvo]
                                     (grid/paivita-osa! solu/*this*
                                                        (fn [solu]
                                                          (assoc solu :nappi-nakyvilla? false)))
                                     (when arvo
                                       (t/paivita-solun-arvo {:paivitettava-asia :aseta-suunnittellut-hankinnat!
                                                              :arvo arvo
                                                              :solu solu/*this*
                                                              :jarjesta-data true
                                                              :triggeroi-seuranta? true}
                                                             true
                                                             hoitokauden-numero)
                                       #_(t/triggeroi-seuranta solu/*this* (keyword (str "hankinnat-yhteenveto-seuranta-" hoitokauden-numero))))
                                     #_(when arvo
                                         (e! (t/->MuutaTaulukonOsanSisarta osa/*this* "Yhteensä" polku-taulukkoon (str (* 12 arvo))))
                                         (e! (t/->MuutaTaulukonOsanSisarta osa/*this* "Indeksikorjattu" polku-taulukkoon (str (t/indeksikorjaa (* 12 arvo)))))
                                         (e! (t/->TallennaKustannusarvoituTyo tallennettava-asia :mhu-johto arvo nil))
                                         (go (>! kaskytyskanava [:tavoite-ja-kattohinta (t/->TallennaJaPaivitaTavoiteSekaKattohinta)])))))
        g (grid/grid-pohjasta g-pohja
                              (fn [g] (e! (tuck-apurit/->MuutaTila [:gridit :suunnittellut-hankinnat :grid] g)))
                              {:haku (fn [] (get-in @tila/suunnittelu-kustannussuunnitelma [:gridit :suunnittellut-hankinnat :grid]))
                               :paivita! (fn [f]
                                           (swap! tila/suunnittelu-kustannussuunnitelma
                                                  (fn [tila]
                                                    (update-in tila [:gridit :suunnittellut-hankinnat :grid] f))))})]
    (grid/rajapinta-grid-yhdistaminen! g
                                       t/suunnittellut-hankinnat-rajapinta
                                       (t/suunnittellut-hankinnat-dr)
                                       (merge {[::g-pohjat/otsikko] {:rajapinta :otsikot
                                                                     :solun-polun-pituus 1
                                                                     :jarjestys [[:nimi :maara :yhteensa :indeksikorjattu]]
                                                                     :datan-kasittely (fn [otsikot]
                                                                                        (mapv (fn [otsikko]
                                                                                                otsikko)
                                                                                              (vals otsikot)))}
                                               [::g-pohjat/yhteenveto] {:rajapinta :yhteensa
                                                                        :solun-polun-pituus 1
                                                                        :jarjestys [[:nimi :maara :yhteensa :indeksikorjattu]]
                                                                        :datan-kasittely (fn [yhteensa]
                                                                                           (mapv (fn [[_ nimi]]
                                                                                                   nimi)
                                                                                                 yhteensa))}}

                                              (reduce (fn [grid-kasittelijat hoitokauden-numero]
                                                        (merge grid-kasittelijat
                                                               {[::g-pohjat/data (dec hoitokauden-numero) ::g-pohjat/data-yhteenveto] {:rajapinta (keyword (str "yhteenveto-" hoitokauden-numero))
                                                                                                                                       :solun-polun-pituus 1
                                                                                                                                       :jarjestys [[:nimi :maara :yhteensa :indeksikorjattu]]
                                                                                                                                       :datan-kasittely (fn [yhteenveto]
                                                                                                                                                          (mapv (fn [[_ v]]
                                                                                                                                                                  v)
                                                                                                                                                                yhteenveto))
                                                                                                                                       :tunnisteen-kasittely (fn [osat _]
                                                                                                                                                               (mapv (fn [osa]
                                                                                                                                                                       (when (instance? solu/Syote osa)
                                                                                                                                                                         :maara))
                                                                                                                                                                     (grid/hae-grid osat :lapset)))}
                                                                [::g-pohjat/data (dec hoitokauden-numero) ::g-pohjat/data-sisalto] {:rajapinta (keyword (str "suunnittellut-hankinnat-" hoitokauden-numero))
                                                                                                                                    :solun-polun-pituus 2
                                                                                                                                    :jarjestys [{:keyfn :aika
                                                                                                                                                 :comp (fn [aika-1 aika-2]
                                                                                                                                                         (pvm/ennen? aika-1 aika-2))}
                                                                                                                                                [:aika :maara :yhteensa :indeksikorjattu]]
                                                                                                                                    :datan-kasittely (fn [vuoden-hoidonjohtopalkkiot]
                                                                                                                                                       (mapv (fn [rivi]
                                                                                                                                                               (mapv (fn [[_ v]]
                                                                                                                                                                       v)
                                                                                                                                                                     rivi))
                                                                                                                                                             vuoden-hoidonjohtopalkkiot))
                                                                                                                                    :tunnisteen-kasittely (fn [data-sisalto-grid data]
                                                                                                                                                            (vec
                                                                                                                                                              (map-indexed (fn [i rivi]
                                                                                                                                                                             (vec
                                                                                                                                                                               (map-indexed (fn [j osa]
                                                                                                                                                                                              (when (or (instance? solu/Syote osa)
                                                                                                                                                                                                        (instance? SyoteTaytaAlas osa))
                                                                                                                                                                                                {:osa :maara
                                                                                                                                                                                                 :aika (:aika (get data j))
                                                                                                                                                                                                 :osan-paikka [i j]}))
                                                                                                                                                                                            (grid/hae-grid rivi :lapset))))
                                                                                                                                                                           (grid/hae-grid data-sisalto-grid :lapset))))}}))
                                                      {}
                                                      (range 1 6))))))

(defn hankinnat-laskutukseen-perustuen-grid []
  (let [g-pohja (hankintojen-pohja "suunnittellut-hankinnat-laskutukseen-perustuen-taulukko"
                                   (fn [hoitokauden-numero rivit-alla arvo]
                                     (when (and arvo (not (empty? rivit-alla)))
                                       (doseq [rivi rivit-alla
                                               :let [maara-solu (grid/get-in-grid rivi [1])
                                                     piillotettu? (grid/piillotettu? rivi)]]
                                         (when-not piillotettu?
                                           (t/paivita-solun-arvo {:paivitettava-asia :aseta-laskutukseen-perustuvat-hankinnat!
                                                                  :arvo arvo
                                                                  :solu maara-solu}
                                                                 true
                                                                 hoitokauden-numero)))))
                                   (fn [hoitokauden-numero arvo]
                                     (when arvo
                                       (t/paivita-solun-arvo {:paivitettava-asia :aseta-laskutukseen-perustuvat-hankinnat!
                                                              :arvo arvo
                                                              :solu solu/*this*}
                                                             false
                                                             hoitokauden-numero)))
                                   (fn [hoitokauden-numero arvo]
                                     (grid/paivita-osa! solu/*this*
                                                        (fn [solu]
                                                          (assoc solu :nappi-nakyvilla? false)))
                                     (when arvo
                                       (t/paivita-solun-arvo {:paivitettava-asia :aseta-laskutukseen-perustuvat-hankinnat!
                                                              :arvo arvo
                                                              :solu solu/*this*
                                                              :jarjesta-data true
                                                              :triggeroi-seuranta? true}
                                                             true
                                                             hoitokauden-numero)
                                       #_(t/triggeroi-seuranta solu/*this* (keyword (str "hankinnat-yhteenveto-seuranta-" hoitokauden-numero))))
                                     #_(when arvo
                                         (e! (t/->MuutaTaulukonOsanSisarta osa/*this* "Yhteensä" polku-taulukkoon (str (* 12 arvo))))
                                         (e! (t/->MuutaTaulukonOsanSisarta osa/*this* "Indeksikorjattu" polku-taulukkoon (str (t/indeksikorjaa (* 12 arvo)))))
                                         (e! (t/->TallennaKustannusarvoituTyo tallennettava-asia :mhu-johto arvo nil))
                                         (go (>! kaskytyskanava [:tavoite-ja-kattohinta (t/->TallennaJaPaivitaTavoiteSekaKattohinta)])))))
        g (grid/grid-pohjasta g-pohja
                              (fn [g] (e! (tuck-apurit/->MuutaTila [:gridit :laskutukseen-perustuvat-hankinnat :grid] g)))
                              {:haku (fn [] (get-in @tila/suunnittelu-kustannussuunnitelma [:gridit :laskutukseen-perustuvat-hankinnat :grid]))
                               :paivita! (fn [f]
                                           (swap! tila/suunnittelu-kustannussuunnitelma
                                                  (fn [tila]
                                                    (update-in tila [:gridit :laskutukseen-perustuvat-hankinnat :grid] f))))})]
    #_(grid/rajapinta-grid-yhdistaminen! g
                                       t/suunnittellut-hankinnat-rajapinta
                                       (t/suunnittellut-hankinnat-dr)
                                       (merge {[::g-pohjat/otsikko] {:rajapinta :otsikot
                                                                     :solun-polun-pituus 1
                                                                     :jarjestys [[:nimi :maara :yhteensa :indeksikorjattu]]
                                                                     :datan-kasittely (fn [otsikot]
                                                                                        (mapv (fn [otsikko]
                                                                                                otsikko)
                                                                                              (vals otsikot)))}
                                               [::g-pohjat/yhteenveto] {:rajapinta :yhteensa
                                                                        :solun-polun-pituus 1
                                                                        :jarjestys [[:nimi :maara :yhteensa :indeksikorjattu]]
                                                                        :datan-kasittely (fn [yhteensa]
                                                                                           (mapv (fn [[_ nimi]]
                                                                                                   nimi)
                                                                                                 yhteensa))}}

                                              (reduce (fn [grid-kasittelijat hoitokauden-numero]
                                                        (merge grid-kasittelijat
                                                               {[::g-pohjat/data (dec hoitokauden-numero) ::g-pohjat/data-yhteenveto] {:rajapinta (keyword (str "yhteenveto-" hoitokauden-numero))
                                                                                                                                       :solun-polun-pituus 1
                                                                                                                                       :jarjestys [[:nimi :maara :yhteensa :indeksikorjattu]]
                                                                                                                                       :datan-kasittely (fn [yhteenveto]
                                                                                                                                                          (mapv (fn [[_ v]]
                                                                                                                                                                  v)
                                                                                                                                                                yhteenveto))
                                                                                                                                       :tunnisteen-kasittely (fn [osat _]
                                                                                                                                                               (mapv (fn [osa]
                                                                                                                                                                       (when (instance? solu/Syote osa)
                                                                                                                                                                         :maara))
                                                                                                                                                                     (grid/hae-grid osat :lapset)))}
                                                                [::g-pohjat/data (dec hoitokauden-numero) ::g-pohjat/data-sisalto] {:rajapinta (keyword (str "suunnittellut-hankinnat-" hoitokauden-numero))
                                                                                                                                    :solun-polun-pituus 2
                                                                                                                                    :jarjestys [{:keyfn :aika
                                                                                                                                                 :comp (fn [aika-1 aika-2]
                                                                                                                                                         (pvm/ennen? aika-1 aika-2))}
                                                                                                                                                [:aika :maara :yhteensa :indeksikorjattu]]
                                                                                                                                    :datan-kasittely (fn [vuoden-hoidonjohtopalkkiot]
                                                                                                                                                       (mapv (fn [rivi]
                                                                                                                                                               (mapv (fn [[_ v]]
                                                                                                                                                                       v)
                                                                                                                                                                     rivi))
                                                                                                                                                             vuoden-hoidonjohtopalkkiot))
                                                                                                                                    :tunnisteen-kasittely (fn [data-sisalto-grid data]
                                                                                                                                                            (vec
                                                                                                                                                              (map-indexed (fn [i rivi]
                                                                                                                                                                             (vec
                                                                                                                                                                               (map-indexed (fn [j osa]
                                                                                                                                                                                              (when (or (instance? solu/Syote osa)
                                                                                                                                                                                                        (instance? SyoteTaytaAlas osa))
                                                                                                                                                                                                {:osa :maara
                                                                                                                                                                                                 :aika (:aika (get data j))
                                                                                                                                                                                                 :osan-paikka [i j]}))
                                                                                                                                                                                            (grid/hae-grid rivi :lapset))))
                                                                                                                                                                           (grid/hae-grid data-sisalto-grid :lapset))))}}))
                                                      {}
                                                      (range 1 6))))
    (grid/aseta-gridin-polut g)))

(defn rahavarausten-grid []
  (let [nyt (pvm/nyt)
        dom-id "rahavaraukset-taulukko"
        yhteenveto-grid-rajapinta-asetukset (fn [vanhat-kasittelijat]
                                              (println "VANHAT KÄSITTELIJÄT")
                                              (println vanhat-kasittelijat)
                                              (println "VANHA RAJAPINTA: " (:rajapinta (first vanhat-kasittelijat)))
                                              {:rajapinta (:rajapinta (first vanhat-kasittelijat))
                                               :solun-polun-pituus 1
                                               :jarjestys [^{:nimi :mapit} [:nimi :maara :yhteensa :indeksikorjattu]]
                                               :datan-kasittely (fn [yhteenveto]
                                                                  (println "----- AFJOAJSFOA ----")
                                                                  (println "YHTEENVETO RAJAPINTA: ")
                                                                  (println "DATA: " yhteenveto)
                                                                  (mapv (fn [[_ v]]
                                                                          v)
                                                                        yhteenveto))
                                               :tunnisteen-kasittely (:tunnisteen-kasittely (first vanhat-kasittelijat))})
        g (grid/grid {:nimi ::root
                      :dom-id dom-id
                      :root-fn (fn [] (get-in @tila/suunnittelu-kustannussuunnitelma [:gridit :rahavaraukset :grid]))
                      :paivita-root! (fn [f]
                                       (swap! tila/suunnittelu-kustannussuunnitelma
                                              (fn [tila]
                                                (update-in tila [:gridit :rahavaraukset :grid] f))))
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
                                                   (assoc-in [:sarake :leveydet] {0 "3fr"
                                                                                  3 "1fr"})
                                                   (assoc-in [:sarake :oletus-leveys] "2fr"))
                                         :osat (mapv (fn [index]
                                                       (if (= 3 index)
                                                         (solu/teksti {:parametrit {:class #{"table-default" "table-default-header" "harmaa-teksti"}}})
                                                         (solu/teksti {:parametrit {:class #{"table-default" "table-default-header"}}})))
                                                     (range 4))
                                         :luokat #{"salli-ylipiirtaminen"}}
                                        [{:sarakkeet [0 4] :rivit [0 1]}])
                             (grid/dynamic-grid {:nimi ::data
                                                 :alueet [{:sarakkeet [0 1] :rivit [0 1]}]
                                                 :koko konf/auto
                                                 :luokat #{"salli-ylipiirtaminen"}
                                                 :lisattiin-osia! (fn [g _] (t/paivita-raidat! (grid/osa-polusta (grid/root g) [::data])))
                                                 :poistettiin-osia! (fn [g] (t/paivita-raidat! (grid/osa-polusta (grid/root g) [::data])))
                                                 :toistettava-osa (grid/grid {:alueet [{:sarakkeet [0 1] :rivit [0 2]}]
                                                                              :koko (-> konf/auto
                                                                                        (assoc-in [:rivi :nimet]
                                                                                                  {::data-yhteenveto 0
                                                                                                   ::data-sisalto 1})
                                                                                        #_(assoc-in [:rivi :korkeudet] {0 "40px"}))
                                                                              :luokat #{"salli-ylipiirtaminen"}
                                                                              :osat [(grid/rivi {:nimi ::data-yhteenveto
                                                                                                 :koko {:seuraa {:seurattava ::otsikko
                                                                                                                 :sarakkeet :sama
                                                                                                                 :rivit :sama}}
                                                                                                 :osat [(solu/laajenna {:aukaise-fn
                                                                                                                        (fn [this auki?]
                                                                                                                          (if auki?
                                                                                                                            (rivi-kuukausifiltterilla! this
                                                                                                                                                       false
                                                                                                                                                       [:. ::t/yhteenveto] yhteenveto-grid-rajapinta-asetukset
                                                                                                                                                       [:. ::t/valinta] {:rajapinta :kuukausitasolla?
                                                                                                                                                                         :solun-polun-pituus 1
                                                                                                                                                                         :datan-kasittely (fn [kuukausitasolla?]
                                                                                                                                                                                            [kuukausitasolla? nil nil nil])})
                                                                                                                            (rivi-ilman-kuukausifiltteria! this
                                                                                                                                                           false
                                                                                                                                                           [:.. ::data-yhteenveto] yhteenveto-grid-rajapinta-asetukset))
                                                                                                                          (t/laajenna-solua-klikattu this auki? dom-id [::data] {:sulkemis-polku [:.. :.. :.. 1]}))
                                                                                                                        :auki-alussa? false
                                                                                                                        :parametrit {:class #{"table-default" "lihavoitu"}}})
                                                                                                        (solu/syote {:toiminnot {:on-change (fn [arvo]
                                                                                                                                              (println "on-change arvo: " arvo)
                                                                                                                                              (when arvo
                                                                                                                                                (when esta-blur_
                                                                                                                                                  (set! esta-blur_ false))
                                                                                                                                                (t/paivita-solun-arvo {:paivitettava-asia :aseta-rahavaraukset-yhteenveto!
                                                                                                                                                                       :arvo arvo
                                                                                                                                                                       :solu solu/*this*
                                                                                                                                                                       :jarjesta-data #{:mapit}})))
                                                                                                                                 :on-focus (fn [event]
                                                                                                                                             (let [arvo (.. event -target -value)]
                                                                                                                                               (println "on-focus arvo: " arvo)
                                                                                                                                               (when (= arvo t/vaihtelua-teksti)
                                                                                                                                                 (set! esta-blur_ true)
                                                                                                                                                 (set! (.. event -target -value) nil))))
                                                                                                                                 :on-blur (fn [arvo]
                                                                                                                                            (println "on-blur arvo: " arvo)
                                                                                                                                            (println "TYYPPI: " (type arvo))
                                                                                                                                            (if esta-blur_
                                                                                                                                              (set! esta-blur_ false)
                                                                                                                                              (when arvo
                                                                                                                                                (println "asetetaann...")
                                                                                                                                                (t/paivita-solun-arvo {:paivitettava-asia :aseta-rahavaraukset!
                                                                                                                                                                       :arvo arvo
                                                                                                                                                                       :solu solu/*this*
                                                                                                                                                                       :jarjesta-data true
                                                                                                                                                                       :triggeroi-seuranta? true}
                                                                                                                                                                      true)))
                                                                                                                                            #_(when arvo
                                                                                                                                                (e! (t/->MuutaTaulukonOsanSisarta osa/*this* "Yhteensä" polku-taulukkoon (str (* 12 arvo))))
                                                                                                                                                (e! (t/->MuutaTaulukonOsanSisarta osa/*this* "Indeksikorjattu" polku-taulukkoon (str (t/indeksikorjaa (* 12 arvo)))))
                                                                                                                                                (e! (t/->TallennaKustannusarvoituTyo tallennettava-asia :mhu-johto arvo nil))
                                                                                                                                                (go (>! kaskytyskanava [:tavoite-ja-kattohinta (t/->TallennaJaPaivitaTavoiteSekaKattohinta)]))))
                                                                                                                                 :on-key-down (fn [event]
                                                                                                                                                (when (= "Enter" (.. event -key))
                                                                                                                                                  (.. event -target blur)))}
                                                                                                                     :kayttaytymiset {:on-change [{:positiivinen-numero {:desimaalien-maara 2}}
                                                                                                                                                  {:eventin-arvo {:f poista-tyhjat}}]
                                                                                                                                      :on-blur [:positiivinen-numero {:eventin-arvo {:f poista-tyhjat}}]}
                                                                                                                     :parametrit {:size 2
                                                                                                                                  :class #{"input-default"}}
                                                                                                                     :fmt yhteenveto-format
                                                                                                                     :fmt-aktiivinen summa-formatointi-aktiivinen})
                                                                                                        (solu/teksti {:parametrit {:class #{"table-default"}}
                                                                                                                      :fmt yhteenveto-format})
                                                                                                        (solu/teksti {:parametrit {:class #{"table-default" "harmaa-teksti"}}
                                                                                                                      :fmt yhteenveto-format})]
                                                                                                 :luokat #{"salli-ylipiirtaminen"}}
                                                                                                [{:sarakkeet [0 4] :rivit [0 1]}])
                                                                                     (grid/taulukko {:nimi ::data-sisalto
                                                                                                     :alueet [{:sarakkeet [0 1] :rivit [0 12]}]
                                                                                                     #_#_:koko konf/livi-oletuskoko
                                                                                                     :luokat #{"piillotettu" "salli-ylipiirtaminen"}}
                                                                                                    (vec
                                                                                                      (repeatedly 12
                                                                                                                  (fn []
                                                                                                                    (grid/rivi {:koko {:seuraa {:seurattava ::otsikko
                                                                                                                                                :sarakkeet :sama
                                                                                                                                                :rivit :sama}}
                                                                                                                                :osat [(solu/teksti {:parametrit {:class #{"table-default"}}
                                                                                                                                                     :fmt (fn [paivamaara]
                                                                                                                                                            (when paivamaara
                                                                                                                                                              (let [teksti (-> paivamaara pvm/kuukausi pvm/kuukauden-lyhyt-nimi (str "/" (pvm/vuosi paivamaara)))
                                                                                                                                                                    mennyt? (and (pvm/ennen? paivamaara nyt)
                                                                                                                                                                                 (or (not= (pvm/kuukausi nyt) (pvm/kuukausi paivamaara))
                                                                                                                                                                                     (not= (pvm/vuosi nyt) (pvm/vuosi paivamaara))))]
                                                                                                                                                                (if mennyt?
                                                                                                                                                                  (str teksti " (mennyt)")
                                                                                                                                                                  teksti))))})
                                                                                                                                       (->SyoteTaytaAlas (gensym "rahavaraus")
                                                                                                                                                         false
                                                                                                                                                         (fn [rivit-alla arvo]
                                                                                                                                                           (when (and arvo (not (empty? rivit-alla)))
                                                                                                                                                             (doseq [rivi rivit-alla
                                                                                                                                                                     :let [maara-solu (grid/get-in-grid rivi [1])
                                                                                                                                                                           piillotettu? (grid/piillotettu? rivi)]]
                                                                                                                                                               (when-not piillotettu?
                                                                                                                                                                 (t/paivita-solun-arvo {:paivitettava-asia :aseta-rahavaraukset!
                                                                                                                                                                                        :arvo arvo
                                                                                                                                                                                        :solu maara-solu}
                                                                                                                                                                                       true)))))
                                                                                                                                                         {:on-change (fn [arvo]
                                                                                                                                                                       (when arvo
                                                                                                                                                                         (t/paivita-solun-arvo {:paivitettava-asia :aseta-rahavaraukset!
                                                                                                                                                                                                :arvo arvo
                                                                                                                                                                                                :solu solu/*this*
                                                                                                                                                                                                :jarjesta-data #{:mapit}}
                                                                                                                                                                                               false)))
                                                                                                                                                          :on-focus (fn [_]
                                                                                                                                                                      (grid/paivita-osa! solu/*this*
                                                                                                                                                                                         (fn [solu]
                                                                                                                                                                                           (assoc solu :nappi-nakyvilla? true))))
                                                                                                                                                          :on-blur (fn [arvo]
                                                                                                                                                                     (grid/paivita-osa! solu/*this*
                                                                                                                                                                                        (fn [solu]
                                                                                                                                                                                          (assoc solu :nappi-nakyvilla? false)))
                                                                                                                                                                     (when arvo
                                                                                                                                                                       (t/paivita-solun-arvo {:paivitettava-asia :aseta-rahavaraukset!
                                                                                                                                                                                              :arvo arvo
                                                                                                                                                                                              :solu solu/*this*
                                                                                                                                                                                              :jarjesta-data true
                                                                                                                                                                                              :triggeroi-seuranta? true}
                                                                                                                                                                                             true)
                                                                                                                                                                       #_(t/triggeroi-seuranta solu/*this* (keyword (str "hankinnat-yhteenveto-seuranta-" hoitokauden-numero))))
                                                                                                                                                                     #_(when arvo
                                                                                                                                                                         (e! (t/->MuutaTaulukonOsanSisarta osa/*this* "Yhteensä" polku-taulukkoon (str (* 12 arvo))))
                                                                                                                                                                         (e! (t/->MuutaTaulukonOsanSisarta osa/*this* "Indeksikorjattu" polku-taulukkoon (str (t/indeksikorjaa (* 12 arvo)))))
                                                                                                                                                                         (e! (t/->TallennaKustannusarvoituTyo tallennettava-asia :mhu-johto arvo nil))
                                                                                                                                                                         (go (>! kaskytyskanava [:tavoite-ja-kattohinta (t/->TallennaJaPaivitaTavoiteSekaKattohinta)]))))
                                                                                                                                                          :on-key-down (fn [event]
                                                                                                                                                                         (when (= "Enter" (.. event -key))
                                                                                                                                                                           (.. event -target blur)))}
                                                                                                                                                         {:on-change [{:positiivinen-numero {:desimaalien-maara 2}}
                                                                                                                                                                      {:eventin-arvo {:f poista-tyhjat}}]
                                                                                                                                                          :on-blur [:positiivinen-numero {:eventin-arvo {:f poista-tyhjat}}]}
                                                                                                                                                         {:size 2
                                                                                                                                                          :class #{"input-default"}}
                                                                                                                                                         summa-formatointi-uusi
                                                                                                                                                         summa-formatointi-aktiivinen)
                                                                                                                                       (solu/teksti {:parametrit {:class #{"table-default"}}
                                                                                                                                                     :fmt summa-formatointi})
                                                                                                                                       (solu/teksti {:parametrit {:class #{"table-default"}}})]
                                                                                                                                :luokat #{"salli-ylipiirtaminen"}}
                                                                                                                               [{:sarakkeet [0 4] :rivit [0 1]}])))))]})})
                             (grid/rivi {:nimi ::yhteenveto
                                         :koko {:seuraa {:seurattava ::otsikko
                                                         :sarakkeet :sama
                                                         :rivit :sama}}
                                         :osat (conj (vec (repeatedly 2 (fn []
                                                                          (solu/teksti {:parametrit {:class #{"table-default" "table-default-sum"}}}))))
                                                     (solu/teksti {:parametrit {:class #{"table-default" "table-default-sum"}}
                                                                   :fmt yhteenveto-format})
                                                     (solu/teksti {:parametrit {:class #{"table-default" "table-default-sum" "harmaa-teksti"}}
                                                                   :fmt yhteenveto-format}))}
                                        [{:sarakkeet [0 4] :rivit [0 1]}])]})]
    (e! (tuck-apurit/->MuutaTila [:gridit :rahavaraukset :grid] g))
    (grid/rajapinta-grid-yhdistaminen! g
                                       t/rahavarausten-rajapinta
                                       (t/rahavarausten-dr)
                                       (merge {[::otsikko] {:rajapinta :rahavaraukset-otsikot
                                                            :solun-polun-pituus 1
                                                            :jarjestys [[:nimi :maara :yhteensa :indeksikorjattu]]
                                                            :datan-kasittely (fn [otsikot]
                                                                               (mapv (fn [otsikko]
                                                                                       otsikko)
                                                                                     (vals otsikot)))}
                                               [::yhteenveto] {:rajapinta :rahavaraukset-yhteensa
                                                               :solun-polun-pituus 1
                                                               :jarjestys [[:nimi :maara :yhteensa :indeksikorjattu]]
                                                               :datan-kasittely (fn [yhteensa]
                                                                                  (mapv (fn [[_ nimi]]
                                                                                          nimi)
                                                                                        yhteensa))}
                                               [::data] {:rajapinta :rahavaraukset
                                                         :solun-polun-pituus 0
                                                         :jarjestys [{:keyfn key
                                                                      :comp (fn [a b]
                                                                              (> (t/rahavaraukset-jarjestys a) (t/rahavaraukset-jarjestys b)))}]
                                                         :luonti (fn [{:keys [arvot valittu-toimenpide]}]
                                                                   (map (fn [[tyyppi _]]
                                                                          (when-not (nil? tyyppi)
                                                                            (let [index (dec (get t/rahavaraukset-jarjestys tyyppi))]
                                                                              {[:. index ::data-yhteenveto] {:rajapinta (keyword (str "rahavaraukset-yhteenveto-" tyyppi "-" valittu-toimenpide))
                                                                                                             :solun-polun-pituus 1
                                                                                                             :jarjestys [^{:nimi :mapit} [:nimi :maara :yhteensa :indeksikorjattu]]
                                                                                                             :datan-kasittely (fn [yhteenveto]
                                                                                                                                (println "YHTEENVETO RAJAPINTA: " (keyword (str "rahavaraukset-yhteenveto-" tyyppi "-" valittu-toimenpide)))
                                                                                                                                (println "DATA: " yhteenveto)
                                                                                                                                (mapv (fn [[_ v]]
                                                                                                                                        v)
                                                                                                                                      yhteenveto))
                                                                                                             :tunnisteen-kasittely (fn [osat _]
                                                                                                                                     (mapv (fn [osa]
                                                                                                                                             (when (instance? solu/Syote osa)
                                                                                                                                               {:osa :maara
                                                                                                                                                :tyyppi tyyppi}))
                                                                                                                                           (grid/hae-grid osat :lapset)))}
                                                                               [:. index ::data-sisalto] {:rajapinta (keyword (str "rahavaraukset-data-" tyyppi "-" valittu-toimenpide))
                                                                                                          :solun-polun-pituus 2
                                                                                                          :jarjestys [{:keyfn :aika
                                                                                                                       :comp (fn [aika-1 aika-2]
                                                                                                                               (when (and aika-1 aika-2)
                                                                                                                                 (pvm/ennen? aika-1 aika-2)))}
                                                                                                                      ^{:nimi :mapit} [:aika :maara :yhteensa :indeksikorjattu]]
                                                                                                          :datan-kasittely (fn [vuoden-hoidonjohtopalkkiot]
                                                                                                                             (println (str "DATA SISÄLTÖ: " (keyword (str "rahavaraukset-data-" tyyppi "-" valittu-toimenpide))))
                                                                                                                             (println vuoden-hoidonjohtopalkkiot)
                                                                                                                             (mapv (fn [rivi]
                                                                                                                                     (mapv (fn [[_ v]]
                                                                                                                                             v)
                                                                                                                                           rivi))
                                                                                                                                   vuoden-hoidonjohtopalkkiot))
                                                                                                          :tunnisteen-kasittely (fn [data-sisalto-grid data]
                                                                                                                                  (vec
                                                                                                                                    (map-indexed (fn [i rivi]
                                                                                                                                                   (vec
                                                                                                                                                     (map-indexed (fn [j osa]
                                                                                                                                                                    (when (or (instance? solu/Syote osa)
                                                                                                                                                                              (instance? SyoteTaytaAlas osa))
                                                                                                                                                                      {:osa :maara
                                                                                                                                                                       :tyyppi tyyppi
                                                                                                                                                                       :osan-paikka [i j]}))
                                                                                                                                                                  (grid/hae-grid rivi :lapset))))
                                                                                                                                                 (grid/hae-grid data-sisalto-grid :lapset))))}})))
                                                                        arvot))
                                                         :datan-kasittely (fn [arvot]
                                                                            {:arvot arvot :valittu-toimenpide (:valittu-toimenpide (meta arvot))})
                                                         #_#_:tunnisteen-kasittely (fn [osat _]
                                                                                     (mapv (fn [osa]
                                                                                             (when (instance? solu/Syote osa)
                                                                                               :maara))
                                                                                           (grid/hae-grid osat :lapset)))}}))))

(defn johto-ja-hallintokorvaus-laskulla-grid []
  (let [g (grid/grid-pohjasta g-pohjat/grid-pohja-5
                              (fn [g]
                                (e! (tuck-apurit/->MuutaTila [:gridit :johto-ja-hallintokorvaus :grid] g)))
                              {:haku (fn [] (get-in @tila/suunnittelu-kustannussuunnitelma [:gridit :johto-ja-hallintokorvaus :grid]))
                               :paivita! (fn [f]
                                           (swap! tila/suunnittelu-kustannussuunnitelma
                                                  (fn [tila]
                                                    (update-in tila [:gridit :johto-ja-hallintokorvaus :grid] f))))})]
    (grid/poista-rivi! g (grid/get-in-grid g [::g-pohjat/yhteenveto]))
    (grid/paivita-grid! (grid/get-in-grid g [::g-pohjat/otsikko])
                        :lapset
                        (fn [lapset]
                          (mapv (fn [lapsi]
                                  (gov/tyhja->teksti lapsi {:class #{"table-default" "table-default-header"}}))
                                lapset)))
    (grid/paivita-grid! (grid/get-in-grid g [::g-pohjat/data])
                        :lapset
                        (fn [lapset]
                          (let [otsikko-data-pohja (first lapset)]
                            (mapv (fn [_]
                                    (let [kopio (grid/samanlainen-osa otsikko-data-pohja)]
                                      (grid/paivita-grid! (grid/get-in-grid kopio [::g-pohjat/data-yhteenveto])
                                                          :lapset
                                                          (fn [lapset]
                                                            [(gov/tyhja->laajenna (get lapset 0)
                                                                                  (fn [this auki?]
                                                                                    (if auki?
                                                                                      (rivi-kuukausifiltterilla! this
                                                                                                                 true
                                                                                                                 #_#_[:. ::t/yhteenveto] yhteenveto-grid-rajapinta-asetukset
                                                                                                                 #_#_[:. ::t/valinta] {:rajapinta :kuukausitasolla?
                                                                                                                                     :solun-polun-pituus 1
                                                                                                                                     :datan-kasittely (fn [kuukausitasolla?]
                                                                                                                                                        (println "DATAN KÄSITTELY: kuukausitasolla?: " kuukausitasolla?)
                                                                                                                                                        [kuukausitasolla? nil nil nil])})
                                                                                      (rivi-ilman-kuukausifiltteria! this
                                                                                                                     true
                                                                                                                     #_#_[:.. ::g-pohjat/data-yhteenveto] yhteenveto-grid-rajapinta-asetukset))
                                                                                    (t/laajenna-solua-klikattu this auki? "johto-ja-hallintokorvaus-laskulla-taulukko" [::g-pohjat/data] {:sulkemis-polku [:.. :.. :.. 1]}))
                                                                                  false
                                                                                  {:class #{"table-default" "lihavoitu"}})
                                                             (gov/tyhja->syote (get lapset 1)
                                                                               {:on-change (fn [arvo]
                                                                                             (when arvo
                                                                                               (t/paivita-solun-arvo {:paivitettava-asia :toimenkuva
                                                                                                                      :arvo arvo
                                                                                                                      :solu solu/*this*}
                                                                                                                     false)))
                                                                                :on-blur (fn [arvo]
                                                                                           (when arvo
                                                                                             (t/paivita-solun-arvo {:paivitettava-asia :toimenkuva
                                                                                                                    :arvo arvo
                                                                                                                    :solu solu/*this*
                                                                                                                    :jarjesta-data true
                                                                                                                    :triggeroi-seuranta? true}
                                                                                                                   true)
                                                                                             #_(t/triggeroi-seuranta solu/*this* :yhteenveto-seuranta))
                                                                                           #_(when arvo
                                                                                               (e! (t/->MuutaTaulukonOsanSisarta osa/*this* "Yhteensä" polku-taulukkoon (str (* 12 arvo))))
                                                                                               (e! (t/->MuutaTaulukonOsanSisarta osa/*this* "Indeksikorjattu" polku-taulukkoon (str (t/indeksikorjaa (* 12 arvo)))))
                                                                                               (e! (t/->TallennaKustannusarvoituTyo tallennettava-asia :mhu-johto arvo nil))
                                                                                               (go (>! kaskytyskanava [:tavoite-ja-kattohinta (t/->TallennaJaPaivitaTavoiteSekaKattohinta)]))))
                                                                                :on-key-down (fn [event]
                                                                                               (when (= "Enter" (.. event -key))
                                                                                                 (.. event -target blur)))}
                                                                               {:on-change [{:positiivinen-numero {:desimaalien-maara 2}}
                                                                                            {:eventin-arvo {:f poista-tyhjat}}]
                                                                                :on-blur [:positiivinen-numero {:eventin-arvo {:f poista-tyhjat}}]}
                                                                               {:size 2
                                                                                :class #{"input-default"}}
                                                                               {:fmt summa-formatointi-uusi
                                                                                :fmt-aktiivinen summa-formatointi-aktiivinen})
                                                             (gov/tyhja->syote (get lapset 2)
                                                                               {:on-change (fn [arvo]
                                                                                             (when arvo
                                                                                               (t/paivita-solun-arvo {:paivitettava-asia :toimenkuva
                                                                                                                      :arvo arvo
                                                                                                                      :solu solu/*this*}
                                                                                                                     false)))
                                                                                :on-blur (fn [arvo]
                                                                                           (when arvo
                                                                                             (t/paivita-solun-arvo {:paivitettava-asia :toimenkuva
                                                                                                                    :arvo arvo
                                                                                                                    :solu solu/*this*
                                                                                                                    :jarjesta-data true
                                                                                                                    :triggeroi-seuranta? true}
                                                                                                                   true)
                                                                                             #_(t/triggeroi-seuranta solu/*this* :yhteenveto-seuranta))
                                                                                           #_(when arvo
                                                                                               (e! (t/->MuutaTaulukonOsanSisarta osa/*this* "Yhteensä" polku-taulukkoon (str (* 12 arvo))))
                                                                                               (e! (t/->MuutaTaulukonOsanSisarta osa/*this* "Indeksikorjattu" polku-taulukkoon (str (t/indeksikorjaa (* 12 arvo)))))
                                                                                               (e! (t/->TallennaKustannusarvoituTyo tallennettava-asia :mhu-johto arvo nil))
                                                                                               (go (>! kaskytyskanava [:tavoite-ja-kattohinta (t/->TallennaJaPaivitaTavoiteSekaKattohinta)]))))
                                                                                :on-key-down (fn [event]
                                                                                               (when (= "Enter" (.. event -key))
                                                                                                 (.. event -target blur)))}
                                                                               {:on-change [{:positiivinen-numero {:desimaalien-maara 2}}
                                                                                            {:eventin-arvo {:f poista-tyhjat}}]
                                                                                :on-blur [:positiivinen-numero {:eventin-arvo {:f poista-tyhjat}}]}
                                                                               {:size 2
                                                                                :class #{"input-default"}}
                                                                               {:fmt summa-formatointi-uusi
                                                                                :fmt-aktiivinen summa-formatointi-aktiivinen})
                                                             (gov/tyhja->teksti (get lapset 3) {:class #{"table-default"}} {:fmt yhteenveto-format})
                                                             (gov/tyhja->teksti (get lapset 4) {:class #{"table-default"}})]))
                                      (grid/paivita-grid! (grid/get-in-grid kopio [::g-pohjat/data-sisalto])
                                                          :lapset
                                                          (fn [lapset]
                                                            (let [rivipohja (first lapset)]
                                                              (vec (repeatedly 12
                                                                               (fn []
                                                                                 (let [rivi (grid/samanlainen-osa rivipohja)]
                                                                                   (grid/paivita-grid! rivi
                                                                                                       :lapset
                                                                                                       (fn [osat]
                                                                                                         [(gov/tyhja->teksti (get osat 0)
                                                                                                                             {:class #{"table-default"}}
                                                                                                                             {:fmt (fn [paivamaara]
                                                                                                                                     paivamaara
                                                                                                                                     #_(let [teksti (-> paivamaara pvm/kuukausi pvm/kuukauden-lyhyt-nimi (str "/" (pvm/vuosi paivamaara)))
                                                                                                                                             mennyt? (and (pvm/ennen? paivamaara nyt)
                                                                                                                                                          (or (not= (pvm/kuukausi nyt) (pvm/kuukausi paivamaara))
                                                                                                                                                              (not= (pvm/vuosi nyt) (pvm/vuosi paivamaara))))]
                                                                                                                                         (if mennyt?
                                                                                                                                           (str teksti " (mennyt)")
                                                                                                                                           teksti)))})
                                                                                                          (tyhja->syote-tayta-alas (get osat 1)
                                                                                                                                   {:nappi-nakyvilla? false
                                                                                                                                    :nappia-painettu! (fn [rivit-alla arvo]
                                                                                                                                                        (when (and arvo (not (empty? rivit-alla)))
                                                                                                                                                          (doseq [rivi rivit-alla
                                                                                                                                                                  :let [maara-solu (grid/get-in-grid rivi [1])
                                                                                                                                                                        piillotettu? (grid/piillotettu? rivi)]]
                                                                                                                                                            (when-not piillotettu?
                                                                                                                                                              (t/paivita-solun-arvo {:paivitettava-asia :toimenkuva
                                                                                                                                                                                     :arvo arvo
                                                                                                                                                                                     :solu maara-solu}
                                                                                                                                                                                    true)))))
                                                                                                                                    :toiminnot {:on-change (fn [arvo]
                                                                                                                                                             (when arvo
                                                                                                                                                               (t/paivita-solun-arvo {:paivitettava-asia :toimenkuva
                                                                                                                                                                                      :arvo arvo
                                                                                                                                                                                      :solu solu/*this*}
                                                                                                                                                                                     false)))
                                                                                                                                                :on-focus (fn [_]
                                                                                                                                                            (grid/paivita-osa! solu/*this*
                                                                                                                                                                               (fn [solu]
                                                                                                                                                                                 (assoc solu :nappi-nakyvilla? true))))
                                                                                                                                                :on-blur (fn [arvo]
                                                                                                                                                           (grid/paivita-osa! solu/*this*
                                                                                                                                                                              (fn [solu]
                                                                                                                                                                                (assoc solu :nappi-nakyvilla? false)))
                                                                                                                                                           (when arvo
                                                                                                                                                             (t/paivita-solun-arvo {:paivitettava-asia :toimenkuva
                                                                                                                                                                                    :arvo arvo
                                                                                                                                                                                    :solu solu/*this*
                                                                                                                                                                                    :jarjesta-data true
                                                                                                                                                                                    :triggeroi-seuranta? true}
                                                                                                                                                                                   true)
                                                                                                                                                             #_(t/triggeroi-seuranta solu/*this* (keyword (str "hankinnat-yhteenveto-seuranta-" hoitokauden-numero))))
                                                                                                                                                           #_(when arvo
                                                                                                                                                               (e! (t/->MuutaTaulukonOsanSisarta osa/*this* "Yhteensä" polku-taulukkoon (str (* 12 arvo))))
                                                                                                                                                               (e! (t/->MuutaTaulukonOsanSisarta osa/*this* "Indeksikorjattu" polku-taulukkoon (str (t/indeksikorjaa (* 12 arvo)))))
                                                                                                                                                               (e! (t/->TallennaKustannusarvoituTyo tallennettava-asia :mhu-johto arvo nil))
                                                                                                                                                               (go (>! kaskytyskanava [:tavoite-ja-kattohinta (t/->TallennaJaPaivitaTavoiteSekaKattohinta)]))))
                                                                                                                                                :on-key-down (fn [event]
                                                                                                                                                               (when (= "Enter" (.. event -key))
                                                                                                                                                                 (.. event -target blur)))}
                                                                                                                                    :kayttaytymiset {:on-change [{:positiivinen-numero {:desimaalien-maara 2}}
                                                                                                                                                                 {:eventin-arvo {:f poista-tyhjat}}]
                                                                                                                                                     :on-blur [:positiivinen-numero {:eventin-arvo {:f poista-tyhjat}}]}
                                                                                                                                    :parametrit {:size 2
                                                                                                                                                 :class #{"input-default"}}
                                                                                                                                    :fmt summa-formatointi-uusi
                                                                                                                                    :fmt-aktiivinen summa-formatointi-aktiivinen})
                                                                                                          (gov/tyhja->teksti (get osat 2) {:class #{"table-default"}})
                                                                                                          (gov/tyhja->teksti (get osat 3) {:class #{"table-default"}})
                                                                                                          (gov/tyhja->teksti (get osat 4) {:class #{"table-default"}})])))))))))
                                      kopio))
                                  t/jh-korvaukset))))
    (grid/aseta-gridin-polut g)
    (grid/paivita-grid! g :koko (fn [koko]
                                  (update koko :rivi (fn [rivi]
                                                       (assoc rivi :korkeudet {0 "40px"})))))
    (grid/paivita-grid! g :parametrit (fn [parametrit] (assoc parametrit :id "johto-ja-hallintokorvaus-laskulla-taulukko")))
    #_(println "johto-ja-hallintokorvaus-laskulla-grid GRIDIN MUOTO")
    #_(grid/pre-walk-grid! g
                           (fn [osa]
                             (println (or (grid/hae-osa osa :nimi)
                                          (grid/hae-osa osa :id)))))
    (-> g
        #_(grid/rajapinta-grid-yhdistaminen! t/suunnittellut-hankinnat-rajapinta
                                             (t/suunnittellut-hankinnat-dr)
                                             (doall (merge {[::g-pohjat/otsikko] {:rajapinta :otsikot
                                                                                  :solun-polun-pituus 1
                                                                                  :jarjestys [[:nimi :maara :yhteensa :indeksikorjattu]]
                                                                                  :datan-kasittely (fn [otsikot]
                                                                                                     (mapv (fn [otsikko]
                                                                                                             otsikko)
                                                                                                           (vals otsikot)))}
                                                            [::g-pohjat/yhteenveto] {:rajapinta :yhteensa
                                                                                     :solun-polun-pituus 1
                                                                                     :jarjestys [[:nimi :maara :yhteensa :indeksikorjattu]]
                                                                                     :datan-kasittely (fn [yhteensa]
                                                                                                        (mapv (fn [[_ nimi]]
                                                                                                                nimi)
                                                                                                              yhteensa))}}

                                                           (doall
                                                             (reduce (fn [grid-kasittelijat hoitokauden-numero]
                                                                       (merge grid-kasittelijat
                                                                              {[::g-pohjat/data (dec hoitokauden-numero) ::g-pohjat/data-yhteenveto] {:rajapinta (keyword (str "yhteenveto-" hoitokauden-numero))
                                                                                                                                                      :solun-polun-pituus 1
                                                                                                                                                      :jarjestys [[:nimi :maara :yhteensa :indeksikorjattu]]
                                                                                                                                                      :datan-kasittely (fn [yhteenveto]
                                                                                                                                                                         (mapv (fn [[_ v]]
                                                                                                                                                                                 v)
                                                                                                                                                                               yhteenveto))
                                                                                                                                                      :tunnisteen-kasittely (fn [osat _]
                                                                                                                                                                              (mapv (fn [osa]
                                                                                                                                                                                      (when (instance? solu/Syote osa)
                                                                                                                                                                                        :maara))
                                                                                                                                                                                    (grid/hae-grid osat :lapset)))}
                                                                               [::g-pohjat/data (dec hoitokauden-numero) ::g-pohjat/data-sisalto] {:rajapinta (keyword (str "suunnittellut-hankinnat-" hoitokauden-numero))
                                                                                                                                                   :solun-polun-pituus 2
                                                                                                                                                   :jarjestys [{:keyfn :aika
                                                                                                                                                                :comp (fn [aika-1 aika-2]
                                                                                                                                                                        (pvm/ennen? aika-1 aika-2))}
                                                                                                                                                               [:aika :maara :yhteensa :indeksikorjattu]]
                                                                                                                                                   :datan-kasittely (fn [vuoden-hoidonjohtopalkkiot]
                                                                                                                                                                      (mapv (fn [rivi]
                                                                                                                                                                              (mapv (fn [[_ v]]
                                                                                                                                                                                      v)
                                                                                                                                                                                    rivi))
                                                                                                                                                                            vuoden-hoidonjohtopalkkiot))
                                                                                                                                                   :tunnisteen-kasittely (fn [data-sisalto-grid data]
                                                                                                                                                                           (vec
                                                                                                                                                                             (map-indexed (fn [i rivi]
                                                                                                                                                                                            (vec
                                                                                                                                                                                              (map-indexed (fn [j osa]
                                                                                                                                                                                                             (when (or (instance? solu/Syote osa)
                                                                                                                                                                                                                       (instance? SyoteTaytaAlas osa))
                                                                                                                                                                                                               {:osa :maara
                                                                                                                                                                                                                :aika (:aika (get data j))
                                                                                                                                                                                                                :osan-paikka [i j]}))
                                                                                                                                                                                                           (grid/hae-grid rivi :lapset))))
                                                                                                                                                                                          (grid/hae-grid data-sisalto-grid :lapset))))}}))
                                                                     {}
                                                                     (range 1 6)))))))))

(defn johto-ja-hallintokorvaus-laskulla-yhteenveto-grid []
  (let [g (grid/grid {:nimi ::root
                      :dom-id "johto-ja-hallintokorvaus-laskulla-yhteenveto-taulukko"
                      :alueet [{:sarakkeet [0 1] :rivit [0 4]}]
                      :koko (assoc-in konf/auto
                                      [:rivi :nimet]
                                      {::otsikko 0
                                       ::data 1
                                       ::yhteenveto 2
                                       ::indeksikorjattu 3})
                      :osat [(grid/rivi {:nimi ::otsikko
                                         :koko (-> konf/livi-oletuskoko
                                                   (assoc-in [:sarake :leveydet] {0 "3fr"
                                                                                  1 "1fr"})
                                                   (assoc-in [:sarake :oletus-leveys] "2fr"))
                                         :osat (repeatedly 7 (fn [] (solu/teksti {:parametrit {:class #{"table-default" "table-default-header"}}})))}
                                        [{:sarakkeet [0 7] :rivit [0 1]}])
                             (grid/taulukko {:nimi ::data
                                             :alueet [{:sarakkeet [0 1] :rivit [0 1]}]
                                             :koko konf/auto}
                                            (mapv (fn []
                                                    (grid/rivi {:koko {:seuraa {:seurattava ::otsikko
                                                                                :sarakkeet :sama
                                                                                :rivit :sama}}
                                                                :osat (repeatedly 7 (fn [] (solu/teksti {:parametrit {:class #{"table-default"}}})))}
                                                               [{:sarakkeet [0 7] :rivit [0 1]}]))
                                                  t/jh-korvaukset))
                             (grid/rivi {:nimi ::yhteenveto
                                         :koko (-> konf/livi-oletuskoko
                                                   (assoc-in [:sarake :leveydet] {0 "3fr"
                                                                                  1 "1fr"})
                                                   (assoc-in [:sarake :oletus-leveys] "2fr"))
                                         :osat (repeatedly 7 (fn [] (solu/teksti {:parametrit {:class #{"table-default" "table-default-header"}}})))}
                                        [{:sarakkeet [0 7] :rivit [0 1]}])
                             (grid/rivi {:nimi ::indeksikorjattu
                                         :koko (-> konf/livi-oletuskoko
                                                   (assoc-in [:sarake :leveydet] {0 "3fr"
                                                                                  1 "1fr"})
                                                   (assoc-in [:sarake :oletus-leveys] "2fr"))
                                         :osat (repeatedly 7 (fn [] (solu/teksti {:parametrit {:class #{"table-default" "table-default-header"}}})))}
                                        [{:sarakkeet [0 7] :rivit [0 1]}])]})]

    (e! (tuck-apurit/->MuutaTila [:gridit :johto-ja-hallintokorvaus-yhteenveto :grid] g))

    #_(println "johto-ja-hallintokorvaus-laskulla-grid GRIDIN MUOTO")
    #_(grid/pre-walk-grid! g
                           (fn [osa]
                             (println (or (grid/hae-osa osa :nimi)
                                          (grid/hae-osa osa :id)))))
    (-> g
        (grid/aseta-root-fn {:haku (fn [] (get-in @tila/suunnittelu-kustannussuunnitelma [:gridit :johto-ja-hallintokorvaus-yhteenveto :grid]))
                             :paivita! (fn [f]
                                         (swap! tila/suunnittelu-kustannussuunnitelma
                                                (fn [tila]
                                                  (update-in tila [:gridit :johto-ja-hallintokorvaus-yhteenveto :grid] f))))})


        grid/aseta-gridin-polut
        #_(grid/rajapinta-grid-yhdistaminen! t/suunnittellut-hankinnat-rajapinta
                                             (t/suunnittellut-hankinnat-dr)
                                             (doall (merge {[::g-pohjat/otsikko] {:rajapinta :otsikot
                                                                                  :solun-polun-pituus 1
                                                                                  :jarjestys [[:nimi :maara :yhteensa :indeksikorjattu]]
                                                                                  :datan-kasittely (fn [otsikot]
                                                                                                     (mapv (fn [otsikko]
                                                                                                             otsikko)
                                                                                                           (vals otsikot)))}
                                                            [::g-pohjat/yhteenveto] {:rajapinta :yhteensa
                                                                                     :solun-polun-pituus 1
                                                                                     :jarjestys [[:nimi :maara :yhteensa :indeksikorjattu]]
                                                                                     :datan-kasittely (fn [yhteensa]
                                                                                                        (mapv (fn [[_ nimi]]
                                                                                                                nimi)
                                                                                                              yhteensa))}}

                                                           (doall
                                                             (reduce (fn [grid-kasittelijat hoitokauden-numero]
                                                                       (merge grid-kasittelijat
                                                                              {[::g-pohjat/data (dec hoitokauden-numero) ::g-pohjat/data-yhteenveto] {:rajapinta (keyword (str "yhteenveto-" hoitokauden-numero))
                                                                                                                                                      :solun-polun-pituus 1
                                                                                                                                                      :jarjestys [[:nimi :maara :yhteensa :indeksikorjattu]]
                                                                                                                                                      :datan-kasittely (fn [yhteenveto]
                                                                                                                                                                         (mapv (fn [[_ v]]
                                                                                                                                                                                 v)
                                                                                                                                                                               yhteenveto))
                                                                                                                                                      :tunnisteen-kasittely (fn [osat _]
                                                                                                                                                                              (mapv (fn [osa]
                                                                                                                                                                                      (when (instance? solu/Syote osa)
                                                                                                                                                                                        :maara))
                                                                                                                                                                                    (grid/hae-grid osat :lapset)))}
                                                                               [::g-pohjat/data (dec hoitokauden-numero) ::g-pohjat/data-sisalto] {:rajapinta (keyword (str "suunnittellut-hankinnat-" hoitokauden-numero))
                                                                                                                                                   :solun-polun-pituus 2
                                                                                                                                                   :jarjestys [{:keyfn :aika
                                                                                                                                                                :comp (fn [aika-1 aika-2]
                                                                                                                                                                        (pvm/ennen? aika-1 aika-2))}
                                                                                                                                                               [:aika :maara :yhteensa :indeksikorjattu]]
                                                                                                                                                   :datan-kasittely (fn [vuoden-hoidonjohtopalkkiot]
                                                                                                                                                                      (mapv (fn [rivi]
                                                                                                                                                                              (mapv (fn [[_ v]]
                                                                                                                                                                                      v)
                                                                                                                                                                                    rivi))
                                                                                                                                                                            vuoden-hoidonjohtopalkkiot))
                                                                                                                                                   :tunnisteen-kasittely (fn [data-sisalto-grid data]
                                                                                                                                                                           (vec
                                                                                                                                                                             (map-indexed (fn [i rivi]
                                                                                                                                                                                            (vec
                                                                                                                                                                                              (map-indexed (fn [j osa]
                                                                                                                                                                                                             (when (or (instance? solu/Syote osa)
                                                                                                                                                                                                                       (instance? SyoteTaytaAlas osa))
                                                                                                                                                                                                               {:osa :maara
                                                                                                                                                                                                                :aika (:aika (get data j))
                                                                                                                                                                                                                :osan-paikka [i j]}))
                                                                                                                                                                                                           (grid/hae-grid rivi :lapset))))
                                                                                                                                                                                          (grid/hae-grid data-sisalto-grid :lapset))))}}))
                                                                     {}
                                                                     (range 1 6)))))))))

(defn hoidonjohtopalkkio-grid []
  (let [yhteenveto-grid-rajapinta-asetukset {:rajapinta :yhteenveto
                                             :solun-polun-pituus 1
                                             :jarjestys [[:nimi :maara :yhteensa :indeksikorjattu]]
                                             :datan-kasittely (fn [yhteenveto]
                                                                (mapv (fn [[_ v]]
                                                                        v)
                                                                      yhteenveto))
                                             :tunnisteen-kasittely (fn [osat _]
                                                                     (mapv (fn [osa]
                                                                             (when (instance? solu/Syote osa)
                                                                               :maara))
                                                                           (grid/hae-grid osat :lapset)))}
        g-pohja (maarataulukon-pohja "hoidonjohtopalkkio-taulukko"
                                     (fn [arvo]
                                       (when arvo
                                         (t/paivita-solun-arvo {:paivitettava-asia :hoidonjohtopalkkio
                                                                :arvo arvo
                                                                :solu solu/*this*}
                                                               false)))
                                     (fn [arvo]
                                       (when arvo
                                         (e! (t/->PaivitaHoidonjohtopalkkiot (js/Number arvo))))
                                       (::triggeroi-seuranta! solu/*this*)
                                       #_(when arvo
                                           (e! (t/->MuutaTaulukonOsanSisarta osa/*this* "Yhteensä" polku-taulukkoon (str (* 12 arvo))))
                                           (e! (t/->MuutaTaulukonOsanSisarta osa/*this* "Indeksikorjattu" polku-taulukkoon (str (t/indeksikorjaa (* 12 arvo)))))
                                           (e! (t/->TallennaKustannusarvoituTyo tallennettava-asia :mhu-johto arvo nil))
                                           (go (>! kaskytyskanava [:tavoite-ja-kattohinta (t/->TallennaJaPaivitaTavoiteSekaKattohinta)]))))
                                     (fn [arvo]
                                       (when arvo
                                         (t/paivita-solun-arvo {:paivitettava-asia :hoidonjohtopalkkio
                                                                :arvo arvo
                                                                :solu solu/*this*}
                                                               false)))
                                     (fn [arvo]
                                       (when arvo
                                         (t/paivita-solun-arvo {:paivitettava-asia :hoidonjohtopalkkio
                                                                :arvo arvo
                                                                :solu solu/*this*
                                                                :jarjesta-data true
                                                                :triggeroi-seuranta? true}
                                                               true)
                                         #_(t/triggeroi-seuranta solu/*this* :yhteenveto-seuranta))
                                       #_(when arvo
                                           (e! (t/->MuutaTaulukonOsanSisarta osa/*this* "Yhteensä" polku-taulukkoon (str (* 12 arvo))))
                                           (e! (t/->MuutaTaulukonOsanSisarta osa/*this* "Indeksikorjattu" polku-taulukkoon (str (t/indeksikorjaa (* 12 arvo)))))
                                           (e! (t/->TallennaKustannusarvoituTyo tallennettava-asia :mhu-johto arvo nil))
                                           (go (>! kaskytyskanava [:tavoite-ja-kattohinta (t/->TallennaJaPaivitaTavoiteSekaKattohinta)])))))
        g (grid/grid-pohjasta g-pohja
                              (fn [g]
                                (swap! tila/suunnittelu-kustannussuunnitelma
                                       (fn [tila]
                                         (assoc-in tila [:gridit :hoidonjohtopalkkio :grid] g)))
                                #_(e! (tuck-apurit/->MuutaTila [:gridit :hoidonjohtopalkkio :grid] g)))
                              {:haku (fn [] (get-in @tila/suunnittelu-kustannussuunnitelma [:gridit :hoidonjohtopalkkio :grid]))
                               :paivita! (fn [f]
                                           (swap! tila/suunnittelu-kustannussuunnitelma
                                                  (fn [tila]
                                                    (update-in tila [:gridit :hoidonjohtopalkkio :grid] f))))})]
    (grid/rajapinta-grid-yhdistaminen! g
                                       t/hoidonjohtopalkkion-rajapinta
                                       (t/hoidonjohtopalkkion-dr)
                                       {[::g-pohjat/otsikko] {:rajapinta :otsikot
                                                              :solun-polun-pituus 1
                                                              :jarjestys [[:nimi :maara :yhteensa :indeksikorjattu]]
                                                              :datan-kasittely (fn [otsikot]
                                                                                 (mapv (fn [otsikko]
                                                                                         otsikko)
                                                                                       (vals otsikot)))}
                                        [::g-pohjat/data 0 ::g-pohjat/data-yhteenveto] yhteenveto-grid-rajapinta-asetukset
                                        [::g-pohjat/data 0 ::g-pohjat/data-sisalto] {:rajapinta :hoidonjohtopalkkio
                                                                                     :solun-polun-pituus 2
                                                                                     :jarjestys [{:keyfn :aika
                                                                                                  :comp (fn [aika-1 aika-2]
                                                                                                          (pvm/ennen? aika-1 aika-2))}
                                                                                                 [:aika :maara :yhteensa :indeksikorjattu]]
                                                                                     :datan-kasittely (fn [vuoden-hoidonjohtopalkkiot]
                                                                                                        (mapv (fn [rivi]
                                                                                                                (mapv (fn [[_ v]]
                                                                                                                        v)
                                                                                                                      rivi))
                                                                                                              vuoden-hoidonjohtopalkkiot))
                                                                                     :tunnisteen-kasittely (fn [data-sisalto-grid data]
                                                                                                             (vec
                                                                                                               (map-indexed (fn [i rivi]
                                                                                                                              (vec
                                                                                                                                (map-indexed (fn [j osa]
                                                                                                                                               (when (instance? solu/Syote osa)
                                                                                                                                                 {:osa :maara
                                                                                                                                                  :aika (:aika (get data j))
                                                                                                                                                  :osan-paikka [i j]}))
                                                                                                                                             (grid/hae-grid rivi :lapset))))
                                                                                                                            (grid/hae-grid data-sisalto-grid :lapset))))}
                                        [::g-pohjat/yhteenveto] {:rajapinta :yhteensa
                                                                 :solun-polun-pituus 1
                                                                 :jarjestys [[:nimi :maara :yhteensa :indeksikorjattu]]
                                                                 :datan-kasittely (fn [yhteensa]
                                                                                    (mapv (fn [[_ nimi]]
                                                                                            nimi)
                                                                                          yhteensa))}})
    (grid/grid-tapahtumat g
                          tila/suunnittelu-kustannussuunnitelma
                          {:hoidonjotopalkkion-disablerivit {:polut [[:gridit :hoidonjohtopalkkio :kuukausitasolla?]]
                                                             :toiminto! (fn [g _ kuukausitasolla?]
                                                                          (maara-solujen-disable! (grid/get-in-grid g [::g-pohjat/data 0 ::g-pohjat/data-sisalto])
                                                                                                  (not kuukausitasolla?))
                                                                          (maara-solujen-disable! (grid/get-in-grid g [::g-pohjat/data 0 0 ::t/yhteenveto])
                                                                                                  kuukausitasolla?))}})))

(defn virhe-datassa
  [data]
  (log "Virheellinen data:\n" (with-out-str (cljs.pprint/pprint data)))
  [:span
   [ikonit/warning]
   " Osaa ei voida näyttää.."])

(defn toimenpiteiden-jarjestys
  [toimenpide]
  (case toimenpide
    :talvihoito 0
    :liikenneympariston-hoito 1
    :sorateiden-hoito 2
    :paallystepaikkaukset 3
    :mhu-yllapito 4
    :mhu-korvausinvestointi 5))

(defn haitari-laatikko [_ {:keys [alussa-auki? aukaise-fn otsikko-elementti]} & _]
  (let [auki? (atom alussa-auki?)
        otsikko-elementti (or otsikko-elementti :span)
        aukaise-fn! (comp (or aukaise-fn identity)
                          (fn [event]
                            (.preventDefault event)
                            (swap! auki? not)))]
    (fn [otsikko {:keys [id]} & sisalto]
      [:div.haitari-laatikko {:id id}
       [otsikko-elementti {:on-click aukaise-fn!
                           :class "klikattava"}
        otsikko
        (if @auki?
          ^{:key "haitari-auki"}
          [ikonit/livicon-chevron-up]
          ^{:key "haitari-kiinni"}
          [ikonit/livicon-chevron-down])]
       (when @auki?
         (doall (map-indexed (fn [index komponentti]
                               (with-meta
                                 komponentti
                                 {:key index}))
                             sisalto)))])))

(defn aseta-rivien-nakyvyys
  [taulukko]
  (p/paivita-arvo taulukko :lapset
                  (fn [rivit]
                    (mapv (fn [rivi]
                            (case (p/rivin-skeema taulukko rivi)
                              :laajenna-lapsilla (p/paivita-arvo rivi :lapset
                                                                 (fn [rivit]
                                                                   (mapv (fn [rivi]
                                                                           (if (empty? (::t/piillotettu rivi))
                                                                             (p/paivita-arvo rivi :class disj "piillotettu")
                                                                             (p/paivita-arvo rivi :class conj "piillotettu")))
                                                                         rivit)))
                              (if (empty? (::t/piillotettu rivi))
                                (p/paivita-arvo rivi :class disj "piillotettu")
                                (p/paivita-arvo rivi :class conj "piillotettu"))))
                          rivit))))

(defn aseta-rivien-taustavari
  ([taulukko] (aseta-rivien-taustavari taulukko 0))
  ([taulukko rivista-eteenpain]
   (p/paivita-arvo taulukko :lapset
                   (fn [rivit]
                     (let [rivien-luokat (fn rivien-luokat [rivit i]
                                           (loop [[rivi & rivit] rivit
                                                  lopputulos []
                                                  i i]
                                             (if (nil? rivi)
                                               [i lopputulos]
                                               (let [konttirivi? (satisfies? p/Jana (first (p/arvo rivi :lapset)))
                                                     piillotettu-rivi? (contains? (p/arvo rivi :class) "piillotettu")
                                                     rivii (if konttirivi?
                                                             (rivien-luokat (p/arvo rivi :lapset) i)
                                                             rivi)]
                                                 (recur rivit
                                                        (if konttirivi?
                                                          (conj lopputulos
                                                                (p/aseta-arvo rivi :lapset (second rivii)))
                                                          (conj lopputulos
                                                                (if piillotettu-rivi?
                                                                  rivi
                                                                  (-> rivi
                                                                      (p/paivita-arvo :class conj (if (odd? i)
                                                                                                    "table-default-odd"
                                                                                                    "table-default-even"))
                                                                      (p/paivita-arvo :class disj (if (odd? i)
                                                                                                    "table-default-even"
                                                                                                    "table-default-odd"))))))
                                                        (cond
                                                          piillotettu-rivi? i
                                                          konttirivi? (if (odd? (first rivii))
                                                                        1 0)
                                                          :else (inc i)))))))]
                       (into []
                             (concat (take rivista-eteenpain rivit)
                                     (second (rivien-luokat (drop rivista-eteenpain rivit) 0)))))))))

(defn hintalaskurisarake
  ([yla ala] [hintalaskurisarake yla ala nil])
  ([yla ala {:keys [wrapper-luokat container-luokat]}]
   ;; Tämä div ottaa sen tasasen tilan muiden sarakkeiden kanssa, jotta vuodet jakautuu tasaisesti
   [:div {:class container-luokat}
    ;; Tämä div taas pitää sisällänsä ylä- ja alarivit, niin että leveys on maksimissaan sisällön leveys.
    ;; Tämä siksi, että ylarivin sisältö voidaan keskittää alariviin nähden
    [:div.sarake-wrapper {:class wrapper-luokat}
     [:div.hintalaskurisarake-yla yla]
     [:div.hintalaskurisarake-ala ala]]]))

(defn hintalaskuri
  [{:keys [otsikko selite hinnat]} {:keys [vuosi]}]
  (if (some #(or (nil? (:summa %))
                 (js/isNaN (:summa %)))
            hinnat)
    [virhe-datassa hinnat]
    [:div.hintalaskuri
     (when otsikko
       [:h5 otsikko])
     (when selite
       [:div selite])
     [:div.hintalaskuri-vuodet
      (for [{:keys [summa hoitokausi teksti]} hinnat]
        ^{:key hoitokausi}
        [hintalaskurisarake (or teksti (str hoitokausi ". vuosi"))
         (fmt/euro summa)
         (when (= hoitokausi vuosi) {:wrapper-luokat "aktiivinen-vuosi"})])
      [hintalaskurisarake " " "=" {:container-luokat "hintalaskuri-yhtakuin"}]
      [hintalaskurisarake "Yhteensä" (fmt/euro (reduce #(+ %1 (:summa %2)) 0 hinnat))]]]))

(defn indeksilaskuri
  ([hinnat indeksit] [indeksilaskuri hinnat indeksit nil])
  ([hinnat indeksit dom-id]
   (let [hinnat (mapv (fn [{:keys [summa hoitokausi]}]
                        (let [{:keys [vuosi]} (get indeksit (dec hoitokausi))
                              indeksikorjattu-summa (t/indeksikorjaa summa hoitokausi)]
                          {:vuosi vuosi
                           :summa indeksikorjattu-summa
                           :hoitokausi hoitokausi}))
                      hinnat)]
     [:div.hintalaskuri.indeksilaskuri {:id dom-id}
      [:span "Indeksikorjattu"]
      [:div.hintalaskuri-vuodet
       (for [{:keys [vuosi summa hoitokausi]} hinnat]
         ^{:key hoitokausi}
         [hintalaskurisarake vuosi (fmt/euro summa)])
       [hintalaskurisarake " " "=" {:container-luokat "hintalaskuri-yhtakuin"}]
       [hintalaskurisarake " " (fmt/euro (reduce #(+ %1 (:summa %2)) 0 hinnat)) {:container-luokat "hintalaskuri-yhteensa"}]]])))

(defn kuluva-hoitovuosi [{:keys [vuosi pvmt]}]
  (if (and vuosi pvmt)
    [:div#kuluva-hoitovuosi
     [:span
      (str "Kuluva hoitovuosi: " vuosi
           ". (" (pvm/pvm (first pvmt))
           " - " (pvm/pvm (second pvmt)) ")")]
     [:div.hoitovuosi-napit
      [napit/yleinen-ensisijainen "Laskutus" #(println "Painettiin Laskutus") {:ikoni [ikonit/euro] :disabled true}]
      [napit/yleinen-ensisijainen "Kustannusten seuranta" #(println "Painettiin Kustannusten seuranta") {:ikoni [ikonit/stats] :disabled true}]]]
    [yleiset/ajax-loader]))

(defn tavoite-ja-kattohinta-sisalto [{{:keys [tavoitehinnat kattohinnat]} :tavoite-ja-kattohinta
                                      :keys [kuluva-hoitokausi indeksit]}]
  (if (and tavoitehinnat kattohinnat indeksit)
    [:div
     [hintalaskuri {:otsikko "Tavoitehinta"
                    :selite "Hankintakustannukset + Erillishankinnat + Johto- ja hallintokorvaus + Hoidonjohtopalkkio"
                    :hinnat (update (vec tavoitehinnat) 0 assoc :teksti "1. vuosi*")}
      kuluva-hoitokausi]
     [indeksilaskuri tavoitehinnat indeksit "tavoitehinnan-indeksikorjaus"]
     [hintalaskuri {:otsikko "Kattohinta"
                    :selite "(Hankintakustannukset + Erillishankinnat + Johto- ja hallintokorvaus + Hoidonjohtopalkkio) x 1,1"
                    :hinnat kattohinnat}
      kuluva-hoitokausi]
     [indeksilaskuri kattohinnat indeksit]]
    [yleiset/ajax-loader]))

(defn suunnitelman-selitteet [this luokat _]
  [:div#suunnitelman-selitteet {:class (apply str (interpose " " luokat))}
   [:span [ikonit/ok] "Kaikki kentätä täytetty"]
   [:span [ikonit/livicon-question] "Keskeneräinen"]
   [:span [ikonit/remove] "Suunnitelma puuttuu"]])

(defn suunnitelmien-taulukko [e!]
  (let [polku-taulukkoon [:suunnitelmien-tila-taulukko]
        osien-paivitys-fn (fn [teksti kuluva-vuosi tuleva-vuosi jakso]
                            (fn [osat]
                              (mapv (fn [osa]
                                      (let [otsikko (p/osan-id osa)]
                                        (case otsikko
                                          "Teksti" (teksti osa)
                                          "Kuluva vuosi" (kuluva-vuosi osa)
                                          "Tuleva vuosi" (tuleva-vuosi osa)
                                          "Jakso" (jakso osa))))
                                    osat)))
        taulukon-paivitys-fn! (fn [paivitetty-taulukko]
                                (swap! tila/suunnittelu-kustannussuunnitelma assoc-in polku-taulukkoon paivitetty-taulukko))
        sarakkeiden-leveys (fn [sarake]
                             (case sarake
                               :teksti "col-xs-12 col-sm-9 col-md-9 col-lg-9"
                               :kuluva-vuosi "col-xs-12 col-sm-1 col-md-1 col-lg-1"
                               :tuleva-vuosi "col-xs-12 col-sm-1 col-md-1 col-lg-1"
                               :jakso "col-xs-12 col-sm-1 col-md-1 col-lg-1"))
        kuluva-hoitokausi (t/kuluva-hoitokausi)
        viimeinen-vuosi? (= 5 (:vuosi kuluva-hoitokausi))
        mene-idlle (fn [id]
                     (.scrollIntoView (dom/getElement id)))
        otsikko-fn (fn [otsikkopohja]
                     (-> otsikkopohja
                         (p/aseta-arvo :id :otsikko-rivi
                                       :class #{})
                         (p/paivita-arvo :lapset
                                         (osien-paivitys-fn (fn [osa]
                                                              (-> osa
                                                                  (p/aseta-arvo :id :otsikko-selite)
                                                                  (assoc :komponentti suunnitelman-selitteet
                                                                         :komponentin-argumentit #{(sarakkeiden-leveys :teksti)})))
                                                            (fn [osa]
                                                              (-> osa
                                                                  (p/aseta-arvo :arvo (if viimeinen-vuosi?
                                                                                        ""
                                                                                        (str (:vuosi kuluva-hoitokausi) ".vuosi"))
                                                                                :class #{(sarakkeiden-leveys :kuluva-vuosi)
                                                                                         (when-not viimeinen-vuosi?
                                                                                           "aktiivinen-vuosi")
                                                                                         "keskita"
                                                                                         "alas"})
                                                                  (p/paivita-arvo :id str "-otsikko")))
                                                            (fn [osa]
                                                              (-> osa
                                                                  (p/aseta-arvo :arvo (str (min 5 (inc (:vuosi kuluva-hoitokausi))) ".vuosi")
                                                                                :class #{(sarakkeiden-leveys :tuleva-vuosi)
                                                                                         (when viimeinen-vuosi?
                                                                                           "aktiivinen-vuosi")
                                                                                         "keskita"
                                                                                         "alas"})
                                                                  (p/paivita-arvo :id str "-otsikko")))
                                                            (fn [osa]
                                                              (-> osa
                                                                  (p/aseta-arvo :arvo ""
                                                                                :class #{(sarakkeiden-leveys :jakso)})
                                                                  (p/paivita-arvo :id str "-otsikko")))))))
        linkkiotsikko-fn (fn [rivin-pohja teksti jakso linkki]
                           (-> rivin-pohja
                               (p/aseta-arvo :id (keyword teksti)
                                             :class #{"suunnitelma-rivi"
                                                      "table-default"})
                               (p/paivita-arvo :lapset
                                               (osien-paivitys-fn (fn [osa]
                                                                    (-> osa
                                                                        (p/aseta-arvo :arvo teksti
                                                                                      :class #{(sarakkeiden-leveys :teksti)
                                                                                               "linkki"})
                                                                        (p/paivita-arvo :id str "-" teksti "-linkkiotsikko")
                                                                        (assoc :teksti teksti)
                                                                        (assoc :toiminnot {:on-click (fn [_] (mene-idlle linkki))})))
                                                                  (fn [osa]
                                                                    (p/aseta-arvo osa
                                                                                  :id (str (gensym "linkkiotsikko"))
                                                                                  :arvo {:ikoni ikonit/remove}
                                                                                  :class #{(sarakkeiden-leveys :kuluva-vuosi)
                                                                                           "keskita"}))
                                                                  (fn [osa]
                                                                    (p/aseta-arvo osa
                                                                                  :id (str (gensym "linkkiotsikko"))
                                                                                  :arvo {:ikoni ikonit/remove}
                                                                                  :class #{(sarakkeiden-leveys :tuleva-vuosi)
                                                                                           "keskita"}))
                                                                  (fn [osa]
                                                                    (p/aseta-arvo osa
                                                                                  :id (str (gensym "linkkiotsikko"))
                                                                                  :arvo jakso
                                                                                  :class #{(sarakkeiden-leveys :jakso)}))))))
        sisaotsikko-fn (fn [rivin-pohja teksti jakso]
                         (-> rivin-pohja
                             (p/aseta-arvo :id (keyword teksti)
                                           :class #{})
                             (p/paivita-arvo :lapset
                                             (osien-paivitys-fn (fn [osa]
                                                                  (p/aseta-arvo osa
                                                                                :id (str (gensym "sisaotsikko"))
                                                                                :arvo teksti
                                                                                :class #{(sarakkeiden-leveys :teksti)}))
                                                                (fn [osa]
                                                                  (p/aseta-arvo osa
                                                                                :id (str (gensym "sisaotsikko"))
                                                                                :arvo {:ikoni ikonit/remove}
                                                                                :class #{(sarakkeiden-leveys :kuluva-vuosi)
                                                                                         "keskita"}))
                                                                (fn [osa]
                                                                  (p/aseta-arvo osa
                                                                                :id (str (gensym "sisaotsikko"))
                                                                                :arvo {:ikoni ikonit/remove}
                                                                                :class #{(sarakkeiden-leveys :tuleva-vuosi)
                                                                                         "keskita"}))
                                                                (fn [osa]
                                                                  (p/aseta-arvo osa
                                                                                :id (str (gensym "sisaotsikko"))
                                                                                :arvo jakso
                                                                                :class #{(sarakkeiden-leveys :jakso)}))))))
        laajenna-toimenpide-fn (fn [laajenna-toimenpide-pohja teksti jakso]
                                 (let [rivi-id (keyword (str teksti "-laajenna"))]
                                   (-> (sisaotsikko-fn laajenna-toimenpide-pohja teksti jakso)
                                       (p/aseta-arvo :id rivi-id
                                                     :class #{"toimenpide-rivi"
                                                              "suunnitelma-rivi"
                                                              "table-default"
                                                              (when viimeinen-vuosi?
                                                                "viimeinen-vuosi")})
                                       (p/paivita-arvo :lapset
                                                       (fn [osat]
                                                         (update osat 0 (fn [laajenna-osa]
                                                                          (-> laajenna-osa
                                                                              (assoc-in [:parametrit :ikoni] "triangle")
                                                                              (p/aseta-arvo :id (str (gensym "laajenna-toimenpide")))
                                                                              (p/paivita-arvo :class conj "ikoni-vasemmalle" "solu-sisenna-1")
                                                                              (assoc :aukaise-fn #(e! (t/->YhteenvetoLaajennaSoluaKlikattu polku-taulukkoon rivi-id %1 %2)))))))))))
        tyyppi->nimi (fn [tyyppi]
                       (case tyyppi
                         :kokonaishintainen-ja-lisatyo "Suunnitellut hankinnat"
                         :vahinkojen-korjaukset "Kolmansien osapuolien aiheuttamien vaurioiden korjaukset"
                         :akillinen-hoitotyo "Äkilliset hoitotyöt"
                         :muut-rahavaraukset "Rahavaraus lupaukseen 1"))
        toimenpide-osa-fn (fn [toimenpide-osa-pohja toimenpideteksti toimenpide]
                            (let [rahavarausrivit (case toimenpide
                                                    (:talvihoito :liikenneympariston-hoito :sorateiden-hoito) [:kokonaishintainen-ja-lisatyo :akillinen-hoitotyo :vahinkojen-korjaukset]
                                                    :mhu-yllapito [:kokonaishintainen-ja-lisatyo :muut-rahavaraukset]
                                                    [:kokonaishintainen-ja-lisatyo])
                                  jaksot (case toimenpide
                                           (:talvihoito :liikenneympariston-hoito :sorateiden-hoito) ["/kk**" "/kk" "/kk"]
                                           :mhu-yllapito ["/kk**" "/kk"]
                                           ["/kk**"])]
                              (map (fn [tyyppi jakso]
                                     (let [rahavarausteksti (tyyppi->nimi tyyppi)]
                                       (-> toimenpide-osa-pohja
                                           (p/aseta-arvo :id (keyword (str toimenpideteksti "-" rahavarausteksti))
                                                         :class #{"piillotettu"
                                                                  "table-default"
                                                                  "suunnitelma-rivi"})
                                           (p/paivita-arvo :lapset
                                                           (osien-paivitys-fn (fn [osa]
                                                                                (p/aseta-arvo osa
                                                                                              :id (str (gensym "toimenpide-osa"))
                                                                                              :arvo rahavarausteksti
                                                                                              :class #{(sarakkeiden-leveys :teksti)
                                                                                                       "solu-sisenna-2"}))
                                                                              (fn [osa]
                                                                                (p/aseta-arvo osa
                                                                                              :id (str (gensym "toimenpide-osa"))
                                                                                              :arvo {:ikoni ikonit/remove}
                                                                                              :class #{(sarakkeiden-leveys :kuluva-vuosi)
                                                                                                       "keskita"}))
                                                                              (fn [osa]
                                                                                (p/aseta-arvo osa
                                                                                              :id (str (gensym "toimenpide-osa"))
                                                                                              :arvo {:ikoni ikonit/remove}
                                                                                              :class #{(sarakkeiden-leveys :tuleva-vuosi)
                                                                                                       "keskita"}))
                                                                              (fn [osa]
                                                                                (p/aseta-arvo osa
                                                                                              :id (str (gensym "toimenpide-osa"))
                                                                                              :arvo jakso
                                                                                              :class #{(sarakkeiden-leveys :jakso)}))))
                                           ;; Laitetaan tämä info, jotta voidaan päivittää pelkästään tarvittaessa render funktiossa
                                           (assoc :suunnitelma tyyppi))))
                                   rahavarausrivit
                                   jaksot)))
        toimenpide-fn (fn [toimenpide-pohja]
                        (map (fn [toimenpide jakso]
                               (let [toimenpideteksti (-> toimenpide name (clj-str/replace #"-" " ") t/aakkosta clj-str/capitalize)]
                                 (-> toimenpide-pohja
                                     (p/aseta-arvo :id (keyword (str toimenpideteksti "-vanhempi"))
                                                   :class #{})
                                     (p/paivita-arvo :lapset
                                                     (fn [rivit]
                                                       (into []
                                                             (reduce (fn [rivit rivin-pohja]
                                                                       (let [rivin-tyyppi (p/janan-id rivin-pohja)]
                                                                         (concat
                                                                           rivit
                                                                           (case rivin-tyyppi
                                                                             :laajenna-toimenpide [(laajenna-toimenpide-fn rivin-pohja toimenpideteksti jakso)]
                                                                             :toimenpide-osa (toimenpide-osa-fn rivin-pohja toimenpideteksti toimenpide)))))
                                                                     [] rivit))))
                                     ;; Laitetaan tämä info, jotta voidaan päivittää pelkästään tarvittaessa render funktiossa
                                     (assoc :toimenpide toimenpide))))
                             (sort-by toimenpiteiden-jarjestys t/toimenpiteet)
                             (repeat (count t/toimenpiteet) "/vuosi")))
        hankintakustannukset-fn (fn [hankintakustannukset-pohja]
                                  (-> hankintakustannukset-pohja
                                      (p/aseta-arvo :id (keyword (str "hankintakustannukset-vanhempi"))
                                                    :class #{})
                                      (p/paivita-arvo :lapset
                                                      (fn [rivit]
                                                        (into []
                                                              (reduce (fn [rivit rivin-pohja]
                                                                        (let [rivin-tyyppi (p/janan-id rivin-pohja)]
                                                                          (concat
                                                                            rivit
                                                                            (case rivin-tyyppi
                                                                              :linkkiotsikko [(linkkiotsikko-fn rivin-pohja "Hankintakustannukset" "/vuosi*" "hankintakustannukset")]
                                                                              :toimenpide (toimenpide-fn rivin-pohja)))))
                                                                      [] rivit))))))
        hallinnollinen-toimenpide-fn (fn [hallinnollinen-toimenpide-pohja]
                                       (map (fn [teksti id jakso]
                                              (-> (linkkiotsikko-fn hallinnollinen-toimenpide-pohja teksti jakso id)
                                                  (p/aseta-arvo :id (keyword (str teksti)))
                                                  (p/paivita-arvo :lapset
                                                                  (fn [osat]
                                                                    (update osat 0 (fn [laajenna-osa]
                                                                                     (p/paivita-arvo laajenna-osa :class conj "solu-sisenna-2")))))
                                                  (assoc :halllinto-id id)))
                                            ["Erillishankinnat"
                                             "Johto- ja hallintokorvaus"
                                             "Toimistokulut"
                                             "Hoidonjohtopalkkio"]
                                            [(:erillishankinnat t/hallinnollisten-idt)
                                             (:johto-ja-hallintokorvaus t/hallinnollisten-idt)
                                             (:toimistokulut-taulukko t/hallinnollisten-idt)
                                             (:hoidonjohtopalkkio t/hallinnollisten-idt)]
                                            (repeat 4 "/kk")))
        hallinnollisetkustannukset-fn (fn [hallinnollisetkustannukset-pohja]
                                        (-> hallinnollisetkustannukset-pohja
                                            (p/aseta-arvo :id (keyword (str "hallinnollisetkustannukset-vanhempi"))
                                                          :class #{})
                                            (p/paivita-arvo :lapset
                                                            (fn [rivit]
                                                              (let [rivin-pohja (first rivit)]
                                                                (into []
                                                                      (concat
                                                                        [(linkkiotsikko-fn rivin-pohja "Hallinnolliset toimenpiteet" "/vuosi" "hallinnolliset-toimenpiteet")]
                                                                        (hallinnollinen-toimenpide-fn rivin-pohja))))))))]
    (muodosta-taulukko :suunnitelmien-taulukko
                       {:otsikko {:janan-tyyppi jana/Rivi
                                  :osat [osa/Komponentti
                                         osa/Teksti
                                         osa/Teksti
                                         osa/Teksti]}
                        :linkkiotsikko {:janan-tyyppi jana/Rivi
                                        :osat [osa/Nappi
                                               osa/Ikoni
                                               osa/Ikoni
                                               osa/Teksti]}
                        :laajenna-toimenpide {:janan-tyyppi jana/Rivi
                                              :osat [osa/Laajenna
                                                     osa/Ikoni
                                                     osa/Ikoni
                                                     osa/Teksti]}
                        :toimenpide-osa {:janan-tyyppi jana/Rivi
                                         :osat [osa/Teksti
                                                osa/Ikoni
                                                osa/Ikoni
                                                osa/Teksti]}
                        :toimenpide {:janan-tyyppi jana/RiviLapsilla
                                     :janat [:laajenna-toimenpide :toimenpide-osa]}
                        :hankintakustannukset {:janan-tyyppi jana/RiviLapsilla
                                               :janat [:linkkiotsikko :toimenpide]}
                        :hallinnollisetkustannukset {:janan-tyyppi jana/RiviLapsilla
                                                     :janat [:linkkiotsikko]}}
                       ["Teksti" "Kuluva vuosi" "Tuleva vuosi" "Jakso"]
                       [:otsikko otsikko-fn :hankintakustannukset hankintakustannukset-fn :hallinnollisetkustannukset hallinnollisetkustannukset-fn]
                       {:taulukon-paivitys-fn! taulukon-paivitys-fn!
                        :id "suunnitelmien-taulukko"
                        :class #{"suunnitelma-ikonien-varit"}})))

(defn suunnitelman-paivitettavat-osat
  [edelliset-taulukot vanhat-hankintakustannukset uudet-hankintakustannukset
   vanhat-hallinnolliset-toimenpiteet uudet-hallinnolliset-toimenpiteet]
  (let [toimenpide-muutokset (fn [hankinnat uudet-toimenpiteet vanhat-toimenpiteet muutoksen-avain]
                               (into {}
                                     (map (fn [[avain uusi] [_ vanha]]
                                            [avain {muutoksen-avain (or (get-in hankinnat [avain muutoksen-avain])
                                                                        (not= uusi vanha))}])
                                          uudet-toimenpiteet
                                          vanhat-toimenpiteet)))
        hallinnolliset-muutokset (fn [hallinnolliset uudet-hallinnolliset vanhat-hallinnolliset muutoksen-avain]
                                   (or (get hallinnolliset muutoksen-avain)
                                       (not= uudet-hallinnolliset vanhat-hallinnolliset)))]
    (-> edelliset-taulukot
        (update :hankinnat (fn [hankinnat]
                             (merge-with merge
                                         (toimenpide-muutokset hankinnat
                                                               (:toimenpiteet uudet-hankintakustannukset)
                                                               (:toimenpiteet vanhat-hankintakustannukset)
                                                               :kokonaishintainen)
                                         (toimenpide-muutokset hankinnat
                                                               (:toimenpiteet-laskutukseen-perustuen uudet-hankintakustannukset)
                                                               (:toimenpiteet-laskutukseen-perustuen vanhat-hankintakustannukset)
                                                               :lisatyo)
                                         (toimenpide-muutokset hankinnat
                                                               (:rahavaraukset uudet-hankintakustannukset)
                                                               (:rahavaraukset vanhat-hankintakustannukset)
                                                               :rahavaraukset)
                                         (into {}
                                               (map (fn [toimenpide]
                                                      [toimenpide {:laskutukseen-perustuen-valinta (or (get-in hankinnat [toimenpide :laskutukseen-perustuen-valinta])
                                                                                                       (and (not= (:laskutukseen-perustuen (:valinnat uudet-hankintakustannukset))
                                                                                                                  (:laskutukseen-perustuen (:valinnat vanhat-hankintakustannukset)))
                                                                                                            (not (contains? (clj-set/intersection (:laskutukseen-perustuen (:valinnat uudet-hankintakustannukset))
                                                                                                                                                  (:laskutukseen-perustuen (:valinnat vanhat-hankintakustannukset)))
                                                                                                                            toimenpide))))}])
                                                    t/toimenpiteet)))))
        (update :hallinnolliset (fn [hallinnolliset]
                                  (merge-with (fn [vanha-muuttunut? uusi-muuttunut?]
                                                (or vanha-muuttunut? uusi-muuttunut?))
                                              hallinnolliset
                                              {(:erillishankinnat t/hallinnollisten-idt) (hallinnolliset-muutokset hallinnolliset
                                                                                                                   (:erillishankinnat uudet-hallinnolliset-toimenpiteet)
                                                                                                                   (:erillishankinnat vanhat-hallinnolliset-toimenpiteet)
                                                                                                                   :erillishankinnat)
                                               (:johto-ja-hallintokorvaus t/hallinnollisten-idt) (hallinnolliset-muutokset hallinnolliset
                                                                                                                           (:johto-ja-hallintokorvaus-yhteenveto uudet-hallinnolliset-toimenpiteet)
                                                                                                                           (:johto-ja-hallintokorvaus-yhteenveto vanhat-hallinnolliset-toimenpiteet)
                                                                                                                           :johto-ja-hallintokorvaus-yhteenveto)
                                               (:toimistokulut-taulukko t/hallinnollisten-idt) (hallinnolliset-muutokset hallinnolliset
                                                                                                                         (:toimistokulut uudet-hallinnolliset-toimenpiteet)
                                                                                                                         (:toimistokulut vanhat-hallinnolliset-toimenpiteet)
                                                                                                                         :toimistokulut)
                                               (:hoidonjohtopalkkio t/hallinnollisten-idt) (hallinnolliset-muutokset hallinnolliset
                                                                                                                     (:johtopalkkio uudet-hallinnolliset-toimenpiteet)
                                                                                                                     (:johtopalkkio vanhat-hallinnolliset-toimenpiteet)
                                                                                                                     :johtopalkkio)}))))))

(defn suunnitelmien-tila
  [e! suunnitelmien-argumentit]
  (let [paivitetyt-taulukot (cljs.core/atom {})]
    (komp/luo
      (komp/piirretty (fn [this]
                        (let [suunnitelmien-taulukko-alkutila (suunnitelmien-taulukko e!)]
                          (e! (tuck-apurit/->MuutaTila [:suunnitelmien-tila-taulukko] suunnitelmien-taulukko-alkutila)))))
      {:should-component-update (fn [this old-argv new-argv]
                                  ;; Tätä argumenttien tarkkailua ei tulisi tehdä tässä, mutta nykyinen reagentin versio tukee vain
                                  ;; :component-will-receive-props metodia, joka olisi toki sopivampi tähän tarkoitukseen,
                                  ;; mutta React on deprecoinut tuon ja se tulee hajottamaan tulevan koodin.
                                  (let [{vanhat-hankintakustannukset :hankintakustannukset
                                         vanhat-hallinnolliset-toimenpiteet :hallinnolliset-toimenpiteet} (last old-argv)
                                        {uudet-hankintakustannukset :hankintakustannukset
                                         uudet-hallinnolliset-toimenpiteet :hallinnolliset-toimenpiteet} (last new-argv)]
                                    (swap! paivitetyt-taulukot (fn [edelliset-taulukot]
                                                                 (suunnitelman-paivitettavat-osat edelliset-taulukot vanhat-hankintakustannukset uudet-hankintakustannukset
                                                                                                  vanhat-hallinnolliset-toimenpiteet uudet-hallinnolliset-toimenpiteet))))
                                  (not= old-argv new-argv))}
      (fn [e! {:keys [kaskytyskanava suunnitelmien-tila-taulukko suunnitelmien-tila-taulukon-tilat-luotu-kerran? kirjoitusoikeus? hankintakustannukset hallinnolliset-toimenpiteet]}]
        (when (and (:toimenpiteet hankintakustannukset) (:johtopalkkio hallinnolliset-toimenpiteet))
          (go (>! kaskytyskanava [:suunnitelmien-tila-render (t/->PaivitaSuunnitelmienTila paivitetyt-taulukot)])))
        (if (and suunnitelmien-tila-taulukko suunnitelmien-tila-taulukon-tilat-luotu-kerran?)
          [p/piirra-taulukko (aseta-rivien-taustavari suunnitelmien-tila-taulukko 1)]
          [yleiset/ajax-loader])))))

(defn maksetaan-filter [_ _]
  (let [kausi-tekstiksi (fn [kausi]
                          (case kausi
                            :kesakausi "Kesäkaudella"
                            :talvikausi "Talvikaudella"
                            :molemmat "Kesä- ja talvikaudella"))]
    (fn [valitse-kausi maksetaan]
      [:div.pudotusvalikko-filter
       [:span "Maksetaan"]
       [yleiset/livi-pudotusvalikko {:valinta maksetaan
                                     :valitse-fn valitse-kausi
                                     :format-fn kausi-tekstiksi
                                     :vayla-tyyli? true}
        [:kesakausi :talvikausi :molemmat]]])))

(defn hankintojen-filter [_]
  (let [toimenpide-tekstiksi (fn [toimenpide]
                               (-> toimenpide name (clj-str/replace #"-" " ") t/aakkosta clj-str/upper-case))
        valitse-toimenpide (r/partial (fn [toimenpide]
                                        (e! (tuck-apurit/->MuutaTila [:suodattimet :hankinnat :toimenpide] toimenpide))
                                        #_(doseq [hoitokauden-numero (range 1 6)]
                                            (t/triggeroi-seuranta (get-in @tila/suunnittelu-kustannussuunnitelma [:gridit :suunnittellut-hankinnat :grid])
                                                                  (keyword (str "hankinnat-yhteenveto-seuranta-" hoitokauden-numero))))
                                        #_(t/triggeroi-seuranta (get-in @tila/suunnittelu-kustannussuunnitelma [:gridit :suunnittellut-hankinnat :grid])
                                                                :hankinnat-yhteensa-seuranta)))
        valitse-kausi (r/partial (fn [kausi]
                                   (e! (tuck-apurit/->MuutaTila [:suodattimet :hankinnat :maksetaan] kausi))
                                   (e! (t/->MaksukausiValittu))))
        vaihda-fn (fn [event]
                    (.preventDefault event)
                    (e! (tuck-apurit/->PaivitaTila [:suodattimet :hankinnat :kopioidaan-tuleville-vuosille?] not)))]
    (fn [{:keys [toimenpide maksetaan kopioidaan-tuleville-vuosille?]}]
      (let [toimenpide (toimenpide-tekstiksi toimenpide)]
        [:div
         [:div.kustannussuunnitelma-filter
          [:div
           [:span "Toimenpide"]
           [yleiset/livi-pudotusvalikko {:valinta toimenpide
                                         :valitse-fn valitse-toimenpide
                                         :format-fn toimenpide-tekstiksi
                                         :vayla-tyyli? true}
            (sort-by toimenpiteiden-jarjestys t/toimenpiteet)]]
          [maksetaan-filter valitse-kausi maksetaan]]
         [:input#kopioi-tuleville-hoitovuosille.vayla-checkbox
          {:type "checkbox" :checked kopioidaan-tuleville-vuosille?
           :on-change vaihda-fn}]
         [:label {:for "kopioi-tuleville-hoitovuosille"}
          "Kopioi kuluvan hoitovuoden summat tuleville vuosille samoille kuukausille"]]))))

(defn yleis-suodatin [e! _]
  (let [yksiloiva-id (str (gensym "kopioi-tuleville-hoitovuosille"))
        hoitovuodet (vec (range 1 6))
        vaihda-fn (fn [event]
                    (.preventDefault event)
                    (e! (tuck-apurit/->PaivitaTila [:suodatin :kopioidaan-tuleville-vuosille?] not)))
        valitse-hoitovuosi (fn [hoitovuosi]
                             (e! (tuck-apurit/->MuutaTila [:suodatin :hoitovuosi] hoitovuosi)))
        hoitovuositeksti (fn [hoitovuosi]
                           (str hoitovuosi ". hoitovuosi"))]
    (fn [_ {:keys [hoitovuosi kopioidaan-tuleville-vuosille?]}]
      (if hoitovuosi
        ^{:key :yleis-suodatin}
        [:div.kustannussuunnitelma-filter
         [:div
          [:input.vayla-checkbox {:id yksiloiva-id
                                  :type "checkbox" :checked kopioidaan-tuleville-vuosille?
                                  :on-change (r/partial vaihda-fn)}]
          [:label {:for yksiloiva-id}
           "Kopioi kuluvan hoitovuoden määrät tuleville vuosille"]]
         [:div.pudotusvalikko-filter
          [:span "Hoitovuosi"]
          [yleiset/livi-pudotusvalikko {:valinta hoitovuosi
                                        :valitse-fn valitse-hoitovuosi
                                        :format-fn hoitovuositeksti
                                        :vayla-tyyli? true}
           hoitovuodet]]]
        [yleiset/ajax-loader]))))

(defn hankintasuunnitelmien-syotto
  "Käytännössä input kenttä, mutta sillä lisäominaisuudella, että fokusoituna, tulee
   'Täytä alas' nappi päälle."
  [this {:keys [input-luokat kaskytyskanava nimi e! laskutuksen-perusteella-taulukko? polku-taulukkoon toimenpide-avain]} value]
  (let [on-change (fn [arvo]
                    (when arvo
                      (e! (t/->PaivitaTaulukonOsa (::tama-komponentti osa/*this*) polku-taulukkoon
                                                  (fn [komponentin-tila]
                                                    (assoc komponentin-tila :value arvo))))))
        on-blur (fn [event]
                  (let [klikattu-elementti (.-relatedTarget event)
                        klikattu-nappia? (and (not (nil? klikattu-elementti))
                                              (or (.getAttribute klikattu-elementti "data-kopioi-allaoleviin")
                                                  (= (str (.getAttribute klikattu-elementti "data-id"))
                                                     (str (p/osan-id this)))))]
                    (e! (t/->PaivitaToimenpideTaulukko (::tama-komponentti osa/*this*) polku-taulukkoon))
                    (e! (t/->PaivitaKustannussuunnitelmanYhteenvedot))
                    ;; Tarkastetaan onko klikattu elementti nappi tai tämä elementti itsessään. Jos on, ei piilloteta nappia.
                    (when-not klikattu-nappia?
                      (e! (t/->TallennaHankintasuunnitelma toimenpide-avain (::tama-komponentti osa/*this*) polku-taulukkoon false laskutuksen-perusteella-taulukko?))
                      (e! (t/->PaivitaTaulukonOsa (::tama-komponentti osa/*this*) polku-taulukkoon
                                                  (fn [komponentin-tila]
                                                    (assoc komponentin-tila :nappi-nakyvilla? false))))
                      (go (>! kaskytyskanava [:tavoite-ja-kattohinta (t/->TallennaJaPaivitaTavoiteSekaKattohinta)])))))
        on-focus (fn [_]
                   (e! (t/->PaivitaTaulukonOsa (::tama-komponentti osa/*this*) polku-taulukkoon
                                               (fn [komponentin-tila]
                                                 (assoc komponentin-tila :nappi-nakyvilla? true)))))
        on-key-down (fn [event]
                      (when (= "Enter" (.. event -key))
                        (.. event -target blur)))
        input-osa (-> (osa/->Syote (keyword (str nimi "-maara-kk"))
                                   {:on-change on-change
                                    :on-blur on-blur
                                    :on-focus on-focus
                                    :on-key-down on-key-down}
                                   {:on-change [{:positiivinen-numero {:desimaalien-maara 2}}
                                                {:eventin-arvo {:f poista-tyhjat}}]}
                                   {:class input-luokat
                                    :type "text"
                                    :value value})
                      (p/lisaa-fmt summa-formatointi)
                      (p/lisaa-fmt-aktiiviselle summa-formatointi-aktiivinen))
        tayta-alas! (fn [this _]
                      (e! (t/->PaivitaTaulukonOsa this polku-taulukkoon
                                                  (fn [komponentin-tila]
                                                    (assoc komponentin-tila :nappi-nakyvilla? false))))
                      (e! (t/->TaytaAlas this polku-taulukkoon))
                      (e! (t/->TallennaHankintasuunnitelma toimenpide-avain this polku-taulukkoon true laskutuksen-perusteella-taulukko?))
                      (go (>! kaskytyskanava [:tavoite-ja-kattohinta (t/->TallennaJaPaivitaTavoiteSekaKattohinta)])))]
    (fn [this {:keys [luokat]} {:keys [value nappi-nakyvilla?]}]
      [:div.kustannus-syotto {:class (apply str (interpose " " luokat))
                              :tab-index -1
                              :data-id (str (p/osan-id this))}
       [napit/yleinen-ensisijainen "Kopioi allaoleviin" (r/partial tayta-alas! this)
        {:luokka (str "kopioi-nappi button-primary-default "
                      (when-not nappi-nakyvilla?
                        "piillotettu"))
         :data-attributes {:data-kopioi-allaoleviin true}
         :tabindex 0}]
       [p/piirra-osa (-> input-osa
                         (p/aseta-arvo :arvo value)
                         (assoc ::tama-komponentti this))]])))

(defn osien-paivitys-fn [nimi maara yhteensa indeksikorjattu]
  (fn [osat]
    (mapv (fn [osa]
            (let [otsikko (p/osan-id osa)]
              (case otsikko
                "Nimi" (nimi osa)
                "Määrä" (maara osa)
                "Yhteensä" (yhteensa osa)
                "Indeksikorjattu" (indeksikorjattu osa))))
          osat)))

(defn hankintojen-taulukko [e! kaskytyskanava toimenpiteet
                            {laskutukseen-perustuen :laskutukseen-perustuen
                             valittu-toimenpide :toimenpide}
                            toimenpide-avain
                            on-oikeus? laskutuksen-perusteella-taulukko?]
  (let [sarakkeiden-leveys (fn [sarake]
                             (case sarake
                               :nimi "col-xs-12 col-sm-6 col-md-6 col-lg-6"
                               :maara-kk "col-xs-12 col-sm-2 col-md-2 col-lg-2"
                               :yhteensa "col-xs-12 col-sm-2 col-md-2 col-lg-2"
                               :indeksikorjattu "col-xs-12 col-sm-2 col-md-2 col-lg-2"))
        polku-taulukkoon (if laskutuksen-perusteella-taulukko?
                           [:hankintakustannukset :toimenpiteet-laskutukseen-perustuen toimenpide-avain]
                           [:hankintakustannukset :toimenpiteet toimenpide-avain])
        taulukon-paivitys-fn! (fn [paivitetty-taulukko]
                                (swap! tila/suunnittelu-kustannussuunnitelma assoc-in polku-taulukkoon paivitetty-taulukko))
        laskutuksen-perusteella? (and (= toimenpide-avain valittu-toimenpide)
                                      (contains? laskutukseen-perustuen toimenpide-avain))
        kuluva-hoitovuosi (:vuosi (t/kuluva-hoitokausi))
        nyt (pvm/nyt)
        hankinnat-hoitokausittain (group-by #(pvm/paivamaaran-hoitokausi (:pvm %))
                                            toimenpiteet)
        otsikko-fn (fn [otsikko-pohja]
                     (-> otsikko-pohja
                         (p/aseta-arvo :id :otsikko-rivi
                                       :class #{"table-default" "table-default-header"})
                         (p/paivita-arvo :lapset
                                         (osien-paivitys-fn (fn [osa]
                                                              (p/aseta-arvo osa
                                                                            :arvo (cond
                                                                                    laskutuksen-perusteella-taulukko? "Laskutuksen perusteella"
                                                                                    laskutuksen-perusteella? "Kiinteät"
                                                                                    :else " ")
                                                                            :class #{(sarakkeiden-leveys :nimi)}))
                                                            (fn [osa]
                                                              (p/aseta-arvo osa
                                                                            :arvo "Määrä €/kk"
                                                                            :class #{(sarakkeiden-leveys :maara-kk)}))
                                                            (fn [osa]
                                                              (p/aseta-arvo osa
                                                                            :arvo "Yhteensä"
                                                                            :class #{(sarakkeiden-leveys :yhteensa)}))
                                                            (fn [osa]
                                                              (p/aseta-arvo osa
                                                                            :arvo "Indeksikorjattu"
                                                                            :class #{(sarakkeiden-leveys :indeksikorjattu)
                                                                                     "harmaa-teksti"}))))))
        paarivi-laajenna (fn [rivin-pohja rivin-id hoitokausi yhteensa]
                           (-> rivin-pohja
                               (p/aseta-arvo :id rivin-id
                                             :class #{"table-default"})
                               (p/paivita-arvo :lapset
                                               (osien-paivitys-fn (fn [osa]
                                                                    (p/aseta-arvo osa
                                                                                  :id (keyword (str rivin-id "-nimi"))
                                                                                  :arvo (if (< hoitokausi kuluva-hoitovuosi)
                                                                                          (str hoitokausi ". hoitovuosi (mennyt)")
                                                                                          (str hoitokausi ". hoitovuosi"))
                                                                                  :class #{(sarakkeiden-leveys :nimi)
                                                                                           "lihavoitu"}))
                                                                  (fn [osa]
                                                                    (p/aseta-arvo osa
                                                                                  :id (keyword (str rivin-id "-maara-kk"))
                                                                                  :arvo ""
                                                                                  :class #{(sarakkeiden-leveys :maara-kk)}))
                                                                  (fn [osa]
                                                                    (-> osa
                                                                        (p/aseta-arvo :id (keyword (str rivin-id "-yhteensa"))
                                                                                      :arvo yhteensa
                                                                                      :class #{(sarakkeiden-leveys :yhteensa)
                                                                                               "lihavoitu"})
                                                                        (p/lisaa-fmt summa-formatointi)))
                                                                  (fn [osa]
                                                                    (let [osa (-> osa
                                                                                  (p/aseta-arvo :id (keyword (str rivin-id "-indeksikorjattu"))
                                                                                                :arvo (t/indeksikorjaa yhteensa hoitokausi)
                                                                                                :class #{(sarakkeiden-leveys :indeksikorjattu)
                                                                                                         "lihavoitu"
                                                                                                         "harmaa-teksti"})
                                                                                  (assoc ::p/lisatty-data {:hoitokausi hoitokausi})
                                                                                  p/luo-tila!
                                                                                  (p/lisaa-fmt summa-formatointi)
                                                                                  (assoc :aukaise-fn #(e! (t/->LaajennaSoluaKlikattu polku-taulukkoon rivin-id %1 %2))))]
                                                                      (when (= hoitokausi kuluva-hoitovuosi)
                                                                        (p/aseta-tila! osa true))
                                                                      osa))))))
        lapsirivi (fn [rivin-pohja paivamaara maara hoitokausi]
                    (-> rivin-pohja
                        (p/aseta-arvo :id (keyword (pvm/pvm paivamaara))
                                      :class #{"table-default"})
                        (assoc ::t/piillotettu (if (= hoitokausi kuluva-hoitovuosi)
                                                 #{} #{:laajenna-kiinni}))
                        (p/paivita-arvo :lapset
                                        (osien-paivitys-fn (fn [osa]
                                                             (-> osa
                                                                 (p/aseta-arvo :id (keyword (str (pvm/pvm paivamaara) "-nimi"))
                                                                               :arvo paivamaara
                                                                               :class #{(sarakkeiden-leveys :nimi) "solu-sisenna-1"})
                                                                 (p/lisaa-fmt (fn [paivamaara]
                                                                                (let [teksti (-> paivamaara pvm/kuukausi pvm/kuukauden-lyhyt-nimi (str "/" (pvm/vuosi paivamaara)))
                                                                                      mennyt? (and (pvm/ennen? paivamaara nyt)
                                                                                                   (or (not= (pvm/kuukausi nyt) (pvm/kuukausi paivamaara))
                                                                                                       (not= (pvm/vuosi nyt) (pvm/vuosi paivamaara))))]
                                                                                  (if mennyt?
                                                                                    (str teksti " (mennyt)")
                                                                                    teksti))))))
                                                           (fn [osa]
                                                             (if on-oikeus?
                                                               (-> osa
                                                                   (p/aseta-arvo :id (keyword (str (pvm/pvm paivamaara) "-maara"))
                                                                                 :arvo {:value (str maara)})
                                                                   (assoc :komponentti hankintasuunnitelmien-syotto
                                                                          :komponentin-argumentit {:e! e!
                                                                                                   :nimi (pvm/pvm paivamaara)
                                                                                                   :laskutuksen-perusteella-taulukko? laskutuksen-perusteella-taulukko?
                                                                                                   :toimenpide-avain toimenpide-avain
                                                                                                   :on-oikeus? on-oikeus?
                                                                                                   :polku-taulukkoon polku-taulukkoon
                                                                                                   :kaskytyskanava kaskytyskanava
                                                                                                   :luokat #{(sarakkeiden-leveys :maara-kk)}
                                                                                                   :input-luokat #{"input-default" "komponentin-input"}}))
                                                               (-> osa
                                                                   (p/aseta-arvo :id (keyword (str (pvm/pvm paivamaara) "-maara"))
                                                                                 :arvo maara
                                                                                 :class #{(sarakkeiden-leveys :maara-kk)})
                                                                   (p/lisaa-fmt summa-formatointi))))
                                                           (fn [osa]
                                                             (-> osa
                                                                 (p/aseta-arvo :id (keyword (str (pvm/pvm paivamaara) "-yhteensa"))
                                                                               :arvo maara
                                                                               :class #{(sarakkeiden-leveys :yhteensa)})
                                                                 (p/lisaa-fmt summa-formatointi)))
                                                           (fn [osa]
                                                             (-> osa
                                                                 (p/aseta-arvo :id (keyword (str (pvm/pvm paivamaara) "-indeksikorjattu"))
                                                                               :arvo ""
                                                                               :class #{(sarakkeiden-leveys :indeksikorjattu)
                                                                                        "harmaa-teksti"})))))))
        laajenna-lapsille-fn (fn [laajenna-lapsille-pohja]
                               (map-indexed (fn [index [_ hoitokauden-hankinnat]]
                                              (let [hoitokausi (inc index)
                                                    laajenna-rivin-id (str hoitokausi)]
                                                (-> laajenna-lapsille-pohja
                                                    (p/aseta-arvo :id laajenna-rivin-id)
                                                    (p/paivita-arvo :lapset
                                                                    (fn [rivit]
                                                                      (into []
                                                                            (reduce (fn [rivit rivin-pohja]
                                                                                      (let [rivin-tyyppi (p/janan-id rivin-pohja)]
                                                                                        (concat
                                                                                          rivit
                                                                                          (case rivin-tyyppi
                                                                                            :laajenna [(paarivi-laajenna rivin-pohja (str laajenna-rivin-id "-paa") hoitokausi nil)]
                                                                                            :lapset (map (fn [hankinta]
                                                                                                           (lapsirivi rivin-pohja (:pvm hankinta) (:summa hankinta) hoitokausi))
                                                                                                         hoitokauden-hankinnat)))))
                                                                                    [] rivit))))
                                                    (assoc :hoitokausi hoitokausi))))
                                            hankinnat-hoitokausittain))
        yhteensa-fn (fn [yhteensa-pohja]
                      (-> yhteensa-pohja
                          (p/aseta-arvo :id :yhteensa
                                        :class #{"table-default" "table-default-sum"})
                          (p/paivita-arvo :lapset
                                          (osien-paivitys-fn (fn [osa]
                                                               (p/aseta-arvo osa
                                                                             :id :yhteensa-nimi
                                                                             :arvo "Yhteensä"
                                                                             :class #{(sarakkeiden-leveys :nimi)}))
                                                             (fn [osa]
                                                               (p/aseta-arvo osa
                                                                             :id :yhteensa-maara-kk
                                                                             :arvo ""
                                                                             :class #{(sarakkeiden-leveys :maara-kk)}))
                                                             (fn [osa]
                                                               (-> osa
                                                                   (p/aseta-arvo :id :yhteensa-yhteensa
                                                                                 :class #{(sarakkeiden-leveys :yhteensa)})
                                                                   (p/lisaa-fmt summa-formatointi)))
                                                             (fn [osa]
                                                               (-> osa
                                                                   (p/aseta-arvo :id :yhteensa-indeksikorjattu
                                                                                 :class #{(sarakkeiden-leveys :indeksikorjattu)
                                                                                          "harmaa-teksti"})
                                                                   (p/lisaa-fmt summa-formatointi)))))))]
    (if on-oikeus?
      (muodosta-taulukko (if laskutuksen-perusteella-taulukko?
                           :hankinnat-taulukko-laskutukseen-perustuen
                           :hankinnat-taulukko)
                         {:normaali {:janan-tyyppi jana/Rivi
                                     :osat [osa/Teksti
                                            osa/Teksti
                                            osa/Teksti
                                            osa/Teksti]}
                          :laajenna {:janan-tyyppi jana/Rivi
                                     :osat [osa/Teksti
                                            osa/Teksti
                                            osa/Teksti
                                            osa/Laajenna]}
                          :lapset {:janan-tyyppi jana/Rivi
                                   :osat [osa/Teksti
                                          osa/Komponentti
                                          osa/Teksti
                                          osa/Teksti]}
                          :laajenna-lapsilla {:janan-tyyppi jana/RiviLapsilla
                                              :janat [:laajenna :lapset]}}
                         ["Nimi" "Määrä" "Yhteensä" "Indeksikorjattu"]
                         [:normaali otsikko-fn :laajenna-lapsilla laajenna-lapsille-fn :normaali yhteensa-fn]
                         {:taulukon-paivitys-fn! taulukon-paivitys-fn!
                          :class #{}})
      (muodosta-taulukko (if laskutuksen-perusteella-taulukko?
                           :hankinnat-taulukko-laskutukseen-perustuen
                           :hankinnat-taulukko)
                         {:normaali {:janan-tyyppi jana/Rivi
                                     :osat [osa/Teksti
                                            osa/Teksti
                                            osa/Teksti
                                            osa/Teksti]}
                          :laajenna {:janan-tyyppi jana/Rivi
                                     :osat [osa/Teksti
                                            osa/Teksti
                                            osa/Teksti
                                            osa/Laajenna]}
                          :lapset {:janan-tyyppi jana/Rivi
                                   :osat [osa/Teksti
                                          osa/Teksti
                                          osa/Teksti
                                          osa/Teksti]}
                          :laajenna-lapsilla {:janan-tyyppi jana/RiviLapsilla
                                              :janat [:laajenna :lapset]}}
                         ["Nimi" "Määrä" "Yhteensä" "Indeksikorjattu"]
                         [:normaali otsikko-fn :laajenna-lapsilla laajenna-lapsille-fn :normaali yhteensa-fn]
                         {:taulukon-paivitys-fn! taulukon-paivitys-fn!
                          :class #{}}))))

(defn rahavarausten-taulukko [e! kaskytyskanava toimenpiteet
                              {valittu-toimenpide :toimenpide}
                              toimenpide-avain
                              on-oikeus?]
  (let [sarakkeiden-leveys (fn [sarake]
                             (case sarake
                               :nimi "col-xs-12 col-sm-6 col-md-6 col-lg-6"
                               :maara-kk "col-xs-12 col-sm-2 col-md-2 col-lg-2"
                               :yhteensa "col-xs-12 col-sm-2 col-md-2 col-lg-2"
                               :indeksikorjattu "col-xs-12 col-sm-2 col-md-2 col-lg-2"))
        polku-taulukkoon [:hankintakustannukset :rahavaraukset toimenpide-avain]
        taulukon-paivitys-fn! (fn [paivitetty-taulukko]
                                (swap! tila/suunnittelu-kustannussuunnitelma assoc-in polku-taulukkoon paivitetty-taulukko))
        kuluva-hoitovuosi (:vuosi (t/kuluva-hoitokausi))
        tyyppi->nimi (fn [tyyppi]
                       (case tyyppi
                         "vahinkojen-korjaukset" "Kolmansien osapuolien aih. vaurioiden korjaukset"
                         "akillinen-hoitotyo" "Äkilliset hoitotyöt"
                         "muut-rahavaraukset" "Rahavaraus lupaukseen 1"))
        tyyppi->tallennettava-asia (fn [tyyppi]
                                     (case tyyppi
                                       "vahinkojen-korjaukset" :kolmansien-osapuolten-aiheuttamat-vahingot
                                       "akillinen-hoitotyo" :akilliset-hoitotyot
                                       "muut-rahavaraukset" :rahavaraus-lupaukseen-1))
        otsikko-fn (fn [otsikko-pohja]
                     (-> otsikko-pohja
                         (p/aseta-arvo :id :otsikko-rivi
                                       :class #{"table-default" "table-default-header"})
                         (p/paivita-arvo :lapset
                                         (osien-paivitys-fn (fn [osa]
                                                              (p/aseta-arvo osa
                                                                            :arvo (-> toimenpide-avain name (clj-str/replace #"-" " ") t/aakkosta clj-str/capitalize)
                                                                            :class #{(sarakkeiden-leveys :nimi)}))
                                                            (fn [osa]
                                                              (p/aseta-arvo osa
                                                                            :arvo "Määrä €/kk"
                                                                            :class #{(sarakkeiden-leveys :maara-kk)}))
                                                            (fn [osa]
                                                              (p/aseta-arvo osa
                                                                            :arvo "Yhteensä"
                                                                            :class #{(sarakkeiden-leveys :yhteensa)}))
                                                            (fn [osa]
                                                              (p/aseta-arvo osa
                                                                            :arvo "Indeksikorjattu"
                                                                            :class #{(sarakkeiden-leveys :indeksikorjattu)
                                                                                     "harmaa-teksti"}))))))
        syottorivi-fn (fn [syotto-pohja]
                        (mapv (fn [{:keys [summa tyyppi]}]
                                (-> syotto-pohja
                                    (p/aseta-arvo :class #{"table-default"}
                                                  :id (keyword tyyppi))
                                    ;; Laitetaan tämä info, jotta voidaan päivittää suunnitelma yhteenveto pelkästään tarvittaessa render funktiossa
                                    (assoc :suunnitelma (keyword tyyppi))
                                    (p/paivita-arvo :lapset
                                                    (osien-paivitys-fn (fn [osa]
                                                                         (p/aseta-arvo osa
                                                                                       :id (keyword (str tyyppi "-" (p/osan-id osa)))
                                                                                       :arvo (tyyppi->nimi tyyppi)
                                                                                       :class #{(sarakkeiden-leveys :nimi)}))
                                                                       (fn [osa]
                                                                         (-> osa
                                                                             (p/aseta-arvo :id (keyword (str tyyppi "-" (p/osan-id osa)))
                                                                                           :arvo summa
                                                                                           :class #{(sarakkeiden-leveys :maara-kk)
                                                                                                    "input-default"})
                                                                             (p/lisaa-fmt summa-formatointi)
                                                                             (p/lisaa-fmt-aktiiviselle summa-formatointi-aktiivinen)
                                                                             (assoc :toiminnot {:on-change (fn [arvo]
                                                                                                             (when arvo
                                                                                                               (e! (t/->MuutaTaulukonOsa osa/*this* polku-taulukkoon arvo))))
                                                                                                :on-blur (fn [arvo]
                                                                                                           (when arvo
                                                                                                             (e! (t/->MuutaTaulukonOsanSisarta osa/*this* "Yhteensä" polku-taulukkoon (str (* 12 arvo))))
                                                                                                             (e! (t/->MuutaTaulukonOsanSisarta osa/*this* "Indeksikorjattu" polku-taulukkoon (str (t/indeksikorjaa (* 12 arvo)))))
                                                                                                             (e! (t/->TallennaKustannusarvoituTyo (tyyppi->tallennettava-asia tyyppi) toimenpide-avain arvo nil))
                                                                                                             (e! (t/->PaivitaKustannussuunnitelmanYhteenvedot))
                                                                                                             (go (>! kaskytyskanava [:tavoite-ja-kattohinta (t/->TallennaJaPaivitaTavoiteSekaKattohinta)]))))
                                                                                                :on-key-down (fn [event]
                                                                                                               (when (= "Enter" (.. event -key))
                                                                                                                 (.. event -target blur)))}
                                                                                    :kayttaytymiset {:on-change [{:positiivinen-numero {:desimaalien-maara 2}}
                                                                                                                 {:eventin-arvo {:f poista-tyhjat}}]
                                                                                                     :on-blur [:str->number :numero-pisteella :positiivinen-numero {:eventin-arvo {:f poista-tyhjat}}]})))
                                                                       (fn [osa]
                                                                         (-> osa
                                                                             (p/aseta-arvo :id (keyword (str tyyppi "-" (p/osan-id osa)))
                                                                                           :arvo (* summa 12)
                                                                                           :class #{(sarakkeiden-leveys :yhteensa)})
                                                                             (p/lisaa-fmt summa-formatointi)))
                                                                       (fn [osa]
                                                                         (-> osa
                                                                             (p/aseta-arvo :id (keyword (str tyyppi "-" (p/osan-id osa)))
                                                                                           :arvo (t/indeksikorjaa (* summa 12))
                                                                                           :class #{(sarakkeiden-leveys :indeksikorjattu)
                                                                                                    "harmaa-teksti"})
                                                                             (p/lisaa-fmt summa-formatointi)))))))
                              toimenpiteet))
        rivi-fn (fn [rivi-pohja]
                  (mapv (fn [{:keys [summa tyyppi]}]
                          (-> rivi-pohja
                              (p/aseta-arvo :class #{"table-default"}
                                            :id (keyword tyyppi))
                              ;; Laitetaan tämä info, jotta voidaan päivittää suunnitelma yhteenveto pelkästään tarvittaessa render funktiossa
                              (assoc :suunnitelma (keyword tyyppi))
                              (p/paivita-arvo :lapset
                                              (osien-paivitys-fn (fn [osa]
                                                                   (p/aseta-arvo osa
                                                                                 :id (keyword (str tyyppi "-" (p/osan-id osa)))
                                                                                 :arvo (tyyppi->nimi tyyppi)
                                                                                 :class #{(sarakkeiden-leveys :nimi)}))
                                                                 (fn [osa]
                                                                   (-> osa
                                                                       (p/aseta-arvo :id (keyword (str tyyppi "-" (p/osan-id osa)))
                                                                                     :arvo summa
                                                                                     :class #{(sarakkeiden-leveys :maara-kk)})
                                                                       (p/lisaa-fmt summa-formatointi)))
                                                                 (fn [osa]
                                                                   (-> osa
                                                                       (p/aseta-arvo :id (keyword (str tyyppi "-" (p/osan-id osa)))
                                                                                     :arvo (* summa 12)
                                                                                     :class #{(sarakkeiden-leveys :yhteensa)})
                                                                       (p/lisaa-fmt summa-formatointi)))
                                                                 (fn [osa]
                                                                   (-> osa
                                                                       (p/aseta-arvo :id (keyword (str tyyppi "-" (p/osan-id osa)))
                                                                                     :arvo (t/indeksikorjaa (* summa 12))
                                                                                     :class #{(sarakkeiden-leveys :indeksikorjattu)
                                                                                              "harmaa-teksti"})
                                                                       (p/lisaa-fmt summa-formatointi)))))))
                        toimenpiteet))
        yhteensa-fn (fn [yhteensa-pohja]
                      (-> yhteensa-pohja
                          (p/aseta-arvo :id :yhteensa
                                        :class #{"table-default" "table-default-sum"})
                          (p/paivita-arvo :lapset
                                          (osien-paivitys-fn (fn [osa]
                                                               (p/aseta-arvo osa
                                                                             :id :yhteensa-nimi
                                                                             :arvo "Yhteensä"
                                                                             :class #{(sarakkeiden-leveys :nimi)}))
                                                             (fn [osa]
                                                               (p/aseta-arvo osa
                                                                             :id :yhteensa-maara-kk
                                                                             :arvo ""
                                                                             :class #{(sarakkeiden-leveys :maara-kk)}))
                                                             (fn [osa]
                                                               (-> osa
                                                                   (p/aseta-arvo :id :yhteensa-yhteensa
                                                                                 :class #{(sarakkeiden-leveys :yhteensa)})
                                                                   (p/lisaa-fmt summa-formatointi)))
                                                             (fn [osa]
                                                               (-> osa
                                                                   (p/aseta-arvo :id :yhteensa-indeksikorjattu
                                                                                 :class #{(sarakkeiden-leveys :indeksikorjattu)
                                                                                          "harmaa-teksti"})
                                                                   (p/lisaa-fmt summa-formatointi)))))))]
    (if on-oikeus?
      (muodosta-taulukko :rahavaraukset-taulukko
                         {:normaali {:janan-tyyppi jana/Rivi
                                     :osat [osa/Teksti
                                            osa/Teksti
                                            osa/Teksti
                                            osa/Teksti]}
                          :syottorivi {:janan-tyyppi jana/Rivi
                                       :osat [osa/Teksti
                                              osa/Syote
                                              osa/Teksti
                                              osa/Teksti]}}
                         ["Nimi" "Määrä" "Yhteensä" "Indeksikorjattu"]
                         [:normaali otsikko-fn :syottorivi syottorivi-fn :normaali yhteensa-fn]
                         {:taulukon-paivitys-fn! taulukon-paivitys-fn!
                          :class #{}})
      (muodosta-taulukko :rahavaraukset-taulukko
                         {:normaali {:janan-tyyppi jana/Rivi
                                     :osat [osa/Teksti
                                            osa/Teksti
                                            osa/Teksti
                                            osa/Teksti]}
                          :rivi {:janan-tyyppi jana/Rivi
                                 :osat [osa/Teksti
                                        osa/Teksti
                                        osa/Teksti
                                        osa/Teksti]}}
                         ["Nimi" "Määrä" "Yhteensä" "Indeksikorjattu"]
                         [:normaali otsikko-fn :rivi rivi-fn :normaali yhteensa-fn]
                         {:taulukon-paivitys-fn! taulukon-paivitys-fn!
                          :class #{}}))))

(defn jh-korvauksen-rivit
  [{:keys [toimenkuva maksukausi tunnit-kk tuntipalkka yhteensa-kk kk-v hoitokaudet] :as jh-korvaus}]
  ;; Tässä voi rikastaa kannasta tulevaa tietoa tai lisätä rivejä datan perusteella
  (let [arvot-nolliksi (fn [arvot alku-nollia? n]
                         (into []
                               (concat
                                 (if alku-nollia?
                                   (repeat 0 n)
                                   (take n arvot))
                                 (if alku-nollia?
                                   (drop n arvot)
                                   (repeat 0 (- (count arvot) n))))))
        hoitokausikohtaiset-rivit (cond
                                    ;; Hankintavastaavalla mahdollisesti eri tiedot ensimmäisenä vuonna kuin 2-5
                                    (and (= toimenkuva "hankintavastaava") (= kk-v 12)) [(-> jh-korvaus
                                                                                             (assoc :hoitokaudet #{1})
                                                                                             (update :tunnit-kk arvot-nolliksi false 1)
                                                                                             (update :tuntipalkka arvot-nolliksi false 1)
                                                                                             (update :yhteensa-kk arvot-nolliksi false 1))
                                                                                         (-> jh-korvaus
                                                                                             (update :hoitokaudet disj 1)
                                                                                             (update :tunnit-kk arvot-nolliksi true 1)
                                                                                             (update :tuntipalkka arvot-nolliksi true 1)
                                                                                             (update :yhteensa-kk arvot-nolliksi true 1))]
                                    :else [jh-korvaus])
        toimenkuvan-maksukausiteksti-suluissa (case maksukausi
                                                :kesa "kesäkausi"
                                                :talvi "talvikausi"
                                                nil)
        toimenkuvan-hoitokausiteksti-suluissa (cond
                                                (> (count hoitokausikohtaiset-rivit) 1) (mapv (fn [{:keys [hoitokaudet]}]
                                                                                                (if (= 1 (count hoitokaudet))
                                                                                                  (str (first hoitokaudet) ". sopimusvuosi")
                                                                                                  (str (apply min hoitokaudet) ".-" (apply max hoitokaudet) ". sopimusvuosi")))
                                                                                              hoitokausikohtaiset-rivit)
                                                (= hoitokaudet #{0}) ["ennen urakkaa"]
                                                :else nil)
        sulkutekstit (map (fn [index]
                            (remove nil? [toimenkuvan-maksukausiteksti-suluissa (get toimenkuvan-hoitokausiteksti-suluissa index)]))
                          (range (count hoitokausikohtaiset-rivit)))]
    (map-indexed (fn [index jh-korvaus]
                   (assoc jh-korvaus :muokattu-toimenkuva (if (empty? (nth sulkutekstit index))
                                                            toimenkuva
                                                            (str toimenkuva " (" (apply str (interpose ", " (nth sulkutekstit index))) ")"))))
                 hoitokausikohtaiset-rivit)))

(defn johto-ja-hallintokorvaus-laskulla-taulukko
  [e! kaskytyskanava jh-korvaukset on-oikeus?]
  (let [osien-paivitys-fn (fn [toimenkuva tunnit-kk tuntipalkka yhteensa-kk kk-v]
                            (fn [osat]
                              (mapv (fn [osa]
                                      (let [otsikko (p/osan-id osa)]
                                        (case otsikko
                                          "Toimenkuva" (toimenkuva osa)
                                          "Tunnit/kk" (tunnit-kk osa)
                                          "Tuntipalkka" (tuntipalkka osa)
                                          "Yhteensä/kk" (yhteensa-kk osa)
                                          "kk/v" (kk-v osa))))
                                    osat)))
        {kuluva-hoitovuosi :vuosi} (t/kuluva-hoitokausi)
        polku-taulukkoon [:hallinnolliset-toimenpiteet :johto-ja-hallintokorvaus-laskulla]
        taulukon-paivitys-fn! (fn [paivitetty-taulukko]
                                (swap! tila/suunnittelu-kustannussuunnitelma assoc-in polku-taulukkoon paivitetty-taulukko))
        otsikko-fn (fn [otsikko-pohja]
                     (-> otsikko-pohja
                         (p/aseta-arvo :id :otsikko-rivi
                                       :class #{"table-default" "table-default-header"
                                                "col-xs-12" "col-sm-12" "col-md-12" "col-lg-12"})
                         (p/paivita-arvo :lapset
                                         (osien-paivitys-fn (fn [osa]
                                                              (p/aseta-arvo osa
                                                                            :arvo "Toimenkuva"))
                                                            (fn [osa]
                                                              (p/aseta-arvo osa
                                                                            :arvo "Tunnit/kk, h"))
                                                            (fn [osa]
                                                              (p/aseta-arvo osa
                                                                            :arvo "Tuntipalkka, €"))
                                                            (fn [osa]
                                                              (p/aseta-arvo osa
                                                                            :arvo "Yhteensä/kk"))
                                                            (fn [osa]
                                                              (p/aseta-arvo osa
                                                                            :arvo "kk/v"))))))
        disabloidaanko-rivi? (fn [hoitokaudet]
                               (and (every? false? (map #(>= % kuluva-hoitovuosi) hoitokaudet))
                                    (not (and (= 1 kuluva-hoitovuosi)
                                              (contains? hoitokaudet 0)))))
        syottorivi-fn (fn [syotto-pohja]
                        (into []
                              (mapcat (fn [jh-korvaus]
                                        (map (fn [{:keys [toimenkuva muokattu-toimenkuva maksukausi tunnit-kk tuntipalkka yhteensa-kk kk-v hoitokaudet] :as jh-korvaus}]
                                               (-> syotto-pohja
                                                   (p/aseta-arvo :class #{"table-default"
                                                                          "col-xs-12" "col-sm-12" "col-md-12" "col-lg-12"}
                                                                 :id (keyword muokattu-toimenkuva))
                                                   (assoc ::p/lisatty-data {:toimenkuva toimenkuva
                                                                            :maksukausi maksukausi
                                                                            :hoitokaudet hoitokaudet})
                                                   (p/paivita-arvo :lapset
                                                                   (osien-paivitys-fn (fn [osa]
                                                                                        (-> osa
                                                                                            (p/aseta-arvo :id (keyword (str muokattu-toimenkuva "-" (p/osan-id osa)))
                                                                                                          :arvo muokattu-toimenkuva)
                                                                                            (p/lisaa-fmt clj-str/capitalize)))
                                                                                      (fn [osa]
                                                                                        (p/aseta-arvo
                                                                                          (-> osa
                                                                                              (assoc :toiminnot {:on-change (fn [arvo]
                                                                                                                              (when arvo
                                                                                                                                (e! (t/->MuutaTaulukonOsa osa/*this* polku-taulukkoon arvo))))
                                                                                                                 :on-blur (fn [arvo]
                                                                                                                            (when arvo
                                                                                                                              (e! (t/->MuutaTaulukonOsa osa/*this* polku-taulukkoon arvo))
                                                                                                                              (e! (t/->TallennaJohtoJaHallintokorvaukset osa/*this* polku-taulukkoon))
                                                                                                                              (e! (t/->PaivitaJHRivit osa/*this*))
                                                                                                                              (go (>! kaskytyskanava [:tavoite-ja-kattohinta (t/->TallennaJaPaivitaTavoiteSekaKattohinta)]))))
                                                                                                                 :on-key-down (fn [event]
                                                                                                                                (when (= "Enter" (.. event -key))
                                                                                                                                  (.. event -target blur)))}
                                                                                                     :kayttaytymiset {:on-change [{:positiivinen-numero {:desimaalien-maara 2}}
                                                                                                                                  {:eventin-arvo {:f poista-tyhjat}}]
                                                                                                                      :on-blur [:str->number :numero-pisteella :positiivinen-numero {:eventin-arvo {:f poista-tyhjat}}]})
                                                                                              (p/lisaa-fmt summa-formatointi)
                                                                                              (p/lisaa-fmt-aktiiviselle summa-formatointi-aktiivinen)
                                                                                              (update :parametrit (fn [parametrit]
                                                                                                                    (assoc parametrit :size 2
                                                                                                                           :disabled? (disabloidaanko-rivi? hoitokaudet)))))
                                                                                          :id (keyword (str muokattu-toimenkuva "-" (p/osan-id osa)))
                                                                                          :arvo (get tunnit-kk (dec kuluva-hoitovuosi))
                                                                                          :class #{"input-default"}))
                                                                                      (fn [osa]
                                                                                        (p/aseta-arvo
                                                                                          (-> osa
                                                                                              (assoc :toiminnot {:on-change (fn [arvo]
                                                                                                                              (when arvo
                                                                                                                                (e! (t/->MuutaTaulukonOsa osa/*this* polku-taulukkoon arvo))))
                                                                                                                 :on-blur (fn [arvo]
                                                                                                                            (when arvo
                                                                                                                              (e! (t/->MuutaTaulukonOsa osa/*this* polku-taulukkoon arvo))
                                                                                                                              (e! (t/->TallennaJohtoJaHallintokorvaukset osa/*this* polku-taulukkoon))
                                                                                                                              (e! (t/->PaivitaJHRivit osa/*this*))))
                                                                                                                 :on-key-down (fn [event]
                                                                                                                                (when (= "Enter" (.. event -key))
                                                                                                                                  (.. event -target blur)))}
                                                                                                     :kayttaytymiset {:on-change [{:positiivinen-numero {:desimaalien-maara 2}}
                                                                                                                                  {:eventin-arvo {:f poista-tyhjat}}]
                                                                                                                      :on-blur [:str->number :numero-pisteella :positiivinen-numero {:eventin-arvo {:f poista-tyhjat}}]})
                                                                                              (p/lisaa-fmt summa-formatointi)
                                                                                              (p/lisaa-fmt-aktiiviselle summa-formatointi-aktiivinen)
                                                                                              (update :parametrit (fn [parametrit]
                                                                                                                    (assoc parametrit :size 2
                                                                                                                           :disabled? (disabloidaanko-rivi? hoitokaudet)))))
                                                                                          :id (keyword (str muokattu-toimenkuva "-" (p/osan-id osa)))
                                                                                          :arvo (get tuntipalkka (dec kuluva-hoitovuosi))
                                                                                          :class #{"input-default"}))
                                                                                      (fn [osa]
                                                                                        (-> osa
                                                                                            (p/aseta-arvo :id (keyword (str muokattu-toimenkuva "-" (p/osan-id osa)))
                                                                                                          :arvo (get yhteensa-kk (dec kuluva-hoitovuosi)))
                                                                                            (p/lisaa-fmt summa-formatointi)))
                                                                                      (fn [osa]
                                                                                        (p/aseta-arvo osa
                                                                                                      :id (keyword (str muokattu-toimenkuva "-" (p/osan-id osa)))
                                                                                                      :arvo kk-v))))))
                                             (jh-korvauksen-rivit jh-korvaus)))
                                      jh-korvaukset)))
        rivi-fn (fn [rivi-pohja]
                  (into []
                        (mapcat (fn [jh-korvaus]
                                  (map (fn [{:keys [toimenkuva muokattu-toimenkuva maksukausi tunnit-kk tuntipalkka yhteensa-kk kk-v hoitokaudet] :as jh-korvaus}]
                                         (-> rivi-pohja
                                             (p/aseta-arvo :class #{"table-default"
                                                                    "col-xs-12" "col-sm-12" "col-md-12" "col-lg-12"}
                                                           :id (keyword muokattu-toimenkuva))
                                             (assoc ::p/lisatty-data {:toimenkuva toimenkuva
                                                                      :maksukausi maksukausi
                                                                      :hoitokaudet hoitokaudet})
                                             (p/paivita-arvo :lapset
                                                             (osien-paivitys-fn (fn [osa]
                                                                                  (-> osa
                                                                                      (p/aseta-arvo :id (keyword (str muokattu-toimenkuva "-" (p/osan-id osa)))
                                                                                                    :arvo muokattu-toimenkuva)
                                                                                      (p/lisaa-fmt clj-str/capitalize)))
                                                                                (fn [osa]
                                                                                  (-> osa
                                                                                      (p/aseta-arvo :id (keyword (str muokattu-toimenkuva "-" (p/osan-id osa)))
                                                                                                    :arvo (get tunnit-kk (dec kuluva-hoitovuosi)))
                                                                                      (p/lisaa-fmt summa-formatointi)))
                                                                                (fn [osa]
                                                                                  (-> osa
                                                                                      (p/aseta-arvo :id (keyword (str muokattu-toimenkuva "-" (p/osan-id osa)))
                                                                                                    :arvo (get tuntipalkka (dec kuluva-hoitovuosi)))
                                                                                      (p/lisaa-fmt summa-formatointi)))
                                                                                (fn [osa]
                                                                                  (-> osa
                                                                                      (p/aseta-arvo :id (keyword (str muokattu-toimenkuva "-" (p/osan-id osa)))
                                                                                                    :arvo (get yhteensa-kk (dec kuluva-hoitovuosi)))
                                                                                      (p/lisaa-fmt summa-formatointi)))
                                                                                (fn [osa]
                                                                                  (p/aseta-arvo osa
                                                                                                :id (keyword (str muokattu-toimenkuva "-" (p/osan-id osa)))
                                                                                                :arvo kk-v))))))
                                       (jh-korvauksen-rivit jh-korvaus)))
                                jh-korvaukset)))]
    (if on-oikeus?
      (muodosta-taulukko :jh-laskulla
                         {:otsikko {:janan-tyyppi jana/Rivi
                                    :osat [osa/Teksti
                                           osa/Teksti
                                           osa/Teksti
                                           osa/Teksti
                                           osa/Teksti]}
                          :syottorivi {:janan-tyyppi jana/Rivi
                                       :osat [osa/Teksti
                                              osa/Syote
                                              osa/Syote
                                              osa/Teksti
                                              osa/Teksti]}}
                         ["Toimenkuva" "Tunnit/kk" "Tuntipalkka" "Yhteensä/kk" "kk/v"]
                         [:otsikko otsikko-fn :syottorivi syottorivi-fn]
                         {:taulukon-paivitys-fn! taulukon-paivitys-fn!
                          :class #{}})
      (muodosta-taulukko :jh-laskulla
                         {:otsikko {:janan-tyyppi jana/Rivi
                                    :osat [osa/Teksti
                                           osa/Teksti
                                           osa/Teksti
                                           osa/Teksti
                                           osa/Teksti]}
                          :rivi {:janan-tyyppi jana/Rivi
                                 :osat [osa/Teksti
                                        osa/Teksti
                                        osa/Teksti
                                        osa/Teksti
                                        osa/Teksti]}}
                         ["Toimenkuva" "Tunnit/kk" "Tuntipalkka" "Yhteensä/kk" "kk/v"]
                         [:otsikko otsikko-fn :rivi rivi-fn]
                         {:taulukon-paivitys-fn! taulukon-paivitys-fn!
                          :class #{}}))))

(defn johto-ja-hallintokorvaus-yhteenveto-taulukko
  [e! jh-korvaukset]
  (let [osien-paivitys-fn (fn [toimenkuva kk-v hoitokausi-1 hoitokausi-2 hoitokausi-3 hoitokausi-4 hoitokausi-5]
                            (fn [osat]
                              (mapv (fn [osa]
                                      (let [otsikko (p/osan-id osa)]
                                        (case otsikko
                                          "Toimenkuva" (toimenkuva osa)
                                          "kk/v" (kk-v osa)
                                          "1.vuosi/€" (hoitokausi-1 osa)
                                          "2.vuosi/€" (hoitokausi-2 osa)
                                          "3.vuosi/€" (hoitokausi-3 osa)
                                          "4.vuosi/€" (hoitokausi-4 osa)
                                          "5.vuosi/€" (hoitokausi-5 osa))))
                                    osat)))
        polku-taulukkoon [:hallinnolliset-toimenpiteet :johto-ja-hallintokorvaus-yhteenveto]
        taulukon-paivitys-fn! (fn [paivitetty-taulukko]
                                (swap! tila/suunnittelu-kustannussuunnitelma assoc-in polku-taulukkoon paivitetty-taulukko))
        rivi-fn (fn [rivi-pohja]
                  (into []
                        (cons
                          (-> rivi-pohja
                              (p/aseta-arvo :id :otsikko-rivi
                                            :class #{"table-default" "table-default-header"
                                                     "col-xs-12" "col-sm-12" "col-md-12" "col-lg-12"})
                              (p/paivita-arvo :lapset
                                              (osien-paivitys-fn (fn [osa]
                                                                   (p/aseta-arvo osa
                                                                                 :arvo "Toimenkuva"))
                                                                 (fn [osa]
                                                                   (p/aseta-arvo osa
                                                                                 :arvo "kk/v"))
                                                                 (fn [osa]
                                                                   (p/aseta-arvo osa
                                                                                 :arvo "1.vuosi/€"))
                                                                 (fn [osa]
                                                                   (p/aseta-arvo osa
                                                                                 :arvo "2.vuosi/€"))
                                                                 (fn [osa]
                                                                   (p/aseta-arvo osa
                                                                                 :arvo "3.vuosi/€"))
                                                                 (fn [osa]
                                                                   (p/aseta-arvo osa
                                                                                 :arvo "4.vuosi/€"))
                                                                 (fn [osa]
                                                                   (p/aseta-arvo osa
                                                                                 :arvo "5.vuosi/€")))))
                          (mapcat (fn [jh-korvaus]
                                    (map (fn [{:keys [toimenkuva muokattu-toimenkuva maksukausi tunnit-kk tuntipalkka yhteensa-kk kk-v hoitokaudet] :as jh-korvaus}]
                                           (let [hoitokauden-arvo (fn [hoitokausi]
                                                                    (if (or (contains? hoitokaudet hoitokausi)
                                                                            (and (= 1 hoitokausi)
                                                                                 (contains? hoitokaudet 0)))
                                                                      (* (get yhteensa-kk (dec hoitokausi)) kk-v)
                                                                      ""))]
                                             (-> rivi-pohja
                                                 (p/aseta-arvo :id (keyword muokattu-toimenkuva)
                                                               :class #{"table-default"})
                                                 (assoc :vuodet hoitokaudet)
                                                 (p/paivita-arvo :lapset
                                                                 (osien-paivitys-fn (fn [osa]
                                                                                      (-> osa
                                                                                          (p/aseta-arvo :arvo muokattu-toimenkuva)
                                                                                          (p/lisaa-fmt clj-str/capitalize)))
                                                                                    (fn [osa]
                                                                                      (p/aseta-arvo osa :arvo kk-v))
                                                                                    (fn [osa]
                                                                                      (cond-> osa
                                                                                              true (p/aseta-arvo :arvo (hoitokauden-arvo 1))
                                                                                              (or (contains? hoitokaudet 1)
                                                                                                  (contains? hoitokaudet 0)) (p/lisaa-fmt summa-formatointi)))
                                                                                    (fn [osa]
                                                                                      (cond-> osa
                                                                                              true (p/aseta-arvo :arvo (hoitokauden-arvo 2))
                                                                                              (contains? hoitokaudet 2) (p/lisaa-fmt summa-formatointi)))
                                                                                    (fn [osa]
                                                                                      (cond-> osa
                                                                                              true (p/aseta-arvo :arvo (hoitokauden-arvo 3))
                                                                                              (contains? hoitokaudet 3) (p/lisaa-fmt summa-formatointi)))
                                                                                    (fn [osa]
                                                                                      (cond-> osa
                                                                                              true (p/aseta-arvo :arvo (hoitokauden-arvo 4))
                                                                                              (contains? hoitokaudet 4) (p/lisaa-fmt summa-formatointi)))
                                                                                    (fn [osa]
                                                                                      (cond-> osa
                                                                                              true (p/aseta-arvo :arvo (hoitokauden-arvo 5))
                                                                                              (contains? hoitokaudet 5) (p/lisaa-fmt summa-formatointi))))))))
                                         (jh-korvauksen-rivit jh-korvaus)))
                                  jh-korvaukset))))
        yhteensa-fn (fn [yhteensa-pohja]
                      (let [{:keys [hoitokausi-1 hoitokausi-2 hoitokausi-3 hoitokausi-4 hoitokausi-5]}
                            (clj-set/rename-keys
                              (transduce
                                (comp (mapcat jh-korvauksen-rivit)
                                      (map (fn [{:keys [toimenkuva muokattu-toimenkuva maksukausi tunnit-kk tuntipalkka yhteensa-kk kk-v hoitokaudet] :as jh-korvaus}]
                                             (into {}
                                                   (map (fn [hoitokausi]
                                                          (let [hoitokauden-index (if (= 0 hoitokausi)
                                                                                    0 (dec hoitokausi))]
                                                            [(if (= 0 hoitokausi) 1 hoitokausi)
                                                             (* (get tunnit-kk hoitokauden-index) (get tuntipalkka hoitokauden-index) kk-v)]))
                                                        hoitokaudet)))))
                                (partial merge-with +) {} jh-korvaukset)
                              {1 :hoitokausi-1
                               2 :hoitokausi-2
                               3 :hoitokausi-3
                               4 :hoitokausi-4
                               5 :hoitokausi-5})]
                        [(-> yhteensa-pohja
                             (p/aseta-arvo :id :yhteensa
                                           :class #{"table-default" "table-default-sum"})
                             (p/paivita-arvo :lapset
                                             (osien-paivitys-fn (fn [osa]
                                                                  (p/aseta-arvo osa :arvo "Yhteensä"))
                                                                (fn [osa]
                                                                  (p/aseta-arvo osa :arvo ""))
                                                                (fn [osa]
                                                                  (-> osa
                                                                      (p/aseta-arvo :arvo hoitokausi-1)
                                                                      (p/lisaa-fmt summa-formatointi)))
                                                                (fn [osa]
                                                                  (-> osa
                                                                      (p/aseta-arvo :arvo hoitokausi-2)
                                                                      (p/lisaa-fmt summa-formatointi)))
                                                                (fn [osa]
                                                                  (-> osa
                                                                      (p/aseta-arvo :arvo hoitokausi-3)
                                                                      (p/lisaa-fmt summa-formatointi)))
                                                                (fn [osa]
                                                                  (-> osa
                                                                      (p/aseta-arvo :arvo hoitokausi-4)
                                                                      (p/lisaa-fmt summa-formatointi)))
                                                                (fn [osa]
                                                                  (-> osa
                                                                      (p/aseta-arvo :arvo hoitokausi-5)
                                                                      (p/lisaa-fmt summa-formatointi))))))
                         (-> yhteensa-pohja
                             (p/aseta-arvo :id :yhteensa-indeksikorjattu
                                           :class #{"table-default" "table-default-sum"})
                             (p/paivita-arvo :lapset
                                             (osien-paivitys-fn (fn [osa]
                                                                  (p/aseta-arvo osa
                                                                                :arvo "Indeksikorjattu"
                                                                                :class #{"harmaa-teksti"}))
                                                                (fn [osa]
                                                                  (p/aseta-arvo osa
                                                                                :arvo ""
                                                                                :class #{"harmaa-teksti"}))
                                                                (fn [osa]
                                                                  (-> osa
                                                                      (p/aseta-arvo :arvo (t/indeksikorjaa hoitokausi-1 1)
                                                                                    :class #{"harmaa-teksti"})
                                                                      (p/lisaa-fmt summa-formatointi)))
                                                                (fn [osa]
                                                                  (-> osa
                                                                      (p/aseta-arvo :arvo (t/indeksikorjaa hoitokausi-2 2)
                                                                                    :class #{"harmaa-teksti"})
                                                                      (p/lisaa-fmt summa-formatointi)))
                                                                (fn [osa]
                                                                  (-> osa
                                                                      (p/aseta-arvo :arvo (t/indeksikorjaa hoitokausi-3 3)
                                                                                    :class #{"harmaa-teksti"})
                                                                      (p/lisaa-fmt summa-formatointi)))
                                                                (fn [osa]
                                                                  (-> osa
                                                                      (p/aseta-arvo :arvo (t/indeksikorjaa hoitokausi-4 4)
                                                                                    :class #{"harmaa-teksti"})
                                                                      (p/lisaa-fmt summa-formatointi)))
                                                                (fn [osa]
                                                                  (-> osa
                                                                      (p/aseta-arvo :arvo (t/indeksikorjaa hoitokausi-5 5)
                                                                                    :class #{"harmaa-teksti"})
                                                                      (p/lisaa-fmt summa-formatointi))))))]))]
    (muodosta-taulukko :jh-yhteenveto
                       {:rivi {:janan-tyyppi jana/Rivi
                               :osat [osa/Teksti
                                      osa/Teksti
                                      osa/Teksti
                                      osa/Teksti
                                      osa/Teksti
                                      osa/Teksti
                                      osa/Teksti]}}
                       ["Toimenkuva" "kk/v" "1.vuosi/€" "2.vuosi/€" "3.vuosi/€" "4.vuosi/€" "5.vuosi/€"]
                       [:rivi rivi-fn :rivi yhteensa-fn]
                       {:taulukon-paivitys-fn! taulukon-paivitys-fn!
                        :class #{}})))

(defn maara-kk-taulukko [e! kaskytyskanava polku-taulukkoon rivin-nimi taulukko-elementin-id
                         {:keys [maara-kk yhteensa]} tallennettava-asia on-oikeus?]
  (let [sarakkeiden-leveys (fn [sarake]
                             (case sarake
                               :nimi "col-xs-12 col-sm-6 col-md-6 col-lg-6"
                               :maara-kk "col-xs-12 col-sm-2 col-md-2 col-lg-2"
                               :yhteensa "col-xs-12 col-sm-2 col-md-2 col-lg-2"
                               :indeksikorjattu "col-xs-12 col-sm-2 col-md-2 col-lg-2"))
        taulukon-paivitys-fn! (fn [paivitetty-taulukko]
                                (swap! tila/suunnittelu-kustannussuunnitelma assoc-in polku-taulukkoon paivitetty-taulukko))
        kuluva-hoitovuosi (:vuosi (t/kuluva-hoitokausi))
        otsikko-fn (fn [otsikko-pohja]
                     (-> otsikko-pohja
                         (p/aseta-arvo :id :otsikko-rivi
                                       :class #{"table-default" "table-default-header"})
                         (p/paivita-arvo :lapset
                                         (osien-paivitys-fn (fn [osa]
                                                              (p/aseta-arvo osa
                                                                            :arvo " "
                                                                            :class #{(sarakkeiden-leveys :nimi)}))
                                                            (fn [osa]
                                                              (p/aseta-arvo osa
                                                                            :arvo "Määrä €/kk"
                                                                            :class #{(sarakkeiden-leveys :maara-kk)}))
                                                            (fn [osa]
                                                              (p/aseta-arvo osa
                                                                            :arvo "Yhteensä"
                                                                            :class #{(sarakkeiden-leveys :yhteensa)}))
                                                            (fn [osa]
                                                              (p/aseta-arvo osa
                                                                            :arvo "Indeksikorjattu"
                                                                            :class #{(sarakkeiden-leveys :indeksikorjattu)
                                                                                     "harmaa-teksti"}))))))
        syottorivi-fn (fn [syotto-pohja]
                        (-> syotto-pohja
                            (p/aseta-arvo :class #{"table-default"}
                                          :id (str rivin-nimi "-rivi"))
                            (p/paivita-arvo :lapset
                                            (osien-paivitys-fn (fn [osa]
                                                                 (p/aseta-arvo osa
                                                                               :id (keyword (p/osan-id osa))
                                                                               :arvo rivin-nimi
                                                                               :class #{(sarakkeiden-leveys :nimi)}))
                                                               (fn [osa]
                                                                 (-> osa
                                                                     (assoc-in [:parametrit :size] 2) ;; size laitettu satunnaisesti, jotta input kentän koko voi muuttua ruudun koon muuttuessa
                                                                     (p/aseta-arvo :id (keyword (p/osan-id osa))
                                                                                   :arvo maara-kk
                                                                                   :class #{(sarakkeiden-leveys :maara-kk)
                                                                                            "input-default"})
                                                                     (p/lisaa-fmt summa-formatointi)
                                                                     (p/lisaa-fmt-aktiiviselle summa-formatointi-aktiivinen)
                                                                     (assoc :toiminnot {:on-change (fn [arvo]
                                                                                                     (when arvo
                                                                                                       (e! (t/->MuutaTaulukonOsa osa/*this* polku-taulukkoon arvo))))
                                                                                        :on-blur (fn [arvo]
                                                                                                   (when arvo
                                                                                                     (e! (t/->MuutaTaulukonOsanSisarta osa/*this* "Yhteensä" polku-taulukkoon (str (* 12 arvo))))
                                                                                                     (e! (t/->MuutaTaulukonOsanSisarta osa/*this* "Indeksikorjattu" polku-taulukkoon (str (t/indeksikorjaa (* 12 arvo)))))
                                                                                                     (e! (t/->TallennaKustannusarvoituTyo tallennettava-asia :mhu-johto arvo nil))
                                                                                                     (go (>! kaskytyskanava [:tavoite-ja-kattohinta (t/->TallennaJaPaivitaTavoiteSekaKattohinta)]))))
                                                                                        :on-key-down (fn [event]
                                                                                                       (when (= "Enter" (.. event -key))
                                                                                                         (.. event -target blur)))}
                                                                            :kayttaytymiset {:on-change [{:positiivinen-numero {:desimaalien-maara 2}}
                                                                                                         {:eventin-arvo {:f poista-tyhjat}}]
                                                                                             :on-blur [:str->number :numero-pisteella :positiivinen-numero {:eventin-arvo {:f poista-tyhjat}}]})))
                                                               (fn [osa]
                                                                 (-> osa
                                                                     (p/aseta-arvo :id (keyword (p/osan-id osa))
                                                                                   :arvo yhteensa
                                                                                   :class #{(sarakkeiden-leveys :yhteensa)})
                                                                     (p/lisaa-fmt summa-formatointi)))
                                                               (fn [osa]
                                                                 (-> osa
                                                                     (p/aseta-arvo :id (keyword (p/osan-id osa))
                                                                                   :arvo (t/indeksikorjaa yhteensa)
                                                                                   :class #{(sarakkeiden-leveys :indeksikorjattu)
                                                                                            "harmaa-teksti"})
                                                                     (p/lisaa-fmt summa-formatointi)))))))
        rivi-fn (fn [rivi-pohja]
                  (-> rivi-pohja
                      (p/aseta-arvo :id :rivi
                                    :class #{"table-default"})
                      (p/paivita-arvo :lapset
                                      (osien-paivitys-fn (fn [osa]
                                                           (p/aseta-arvo osa
                                                                         :id (keyword (p/osan-id osa))
                                                                         :arvo rivin-nimi
                                                                         :class #{(sarakkeiden-leveys :nimi)}))
                                                         (fn [osa]
                                                           (-> osa
                                                               (p/aseta-arvo :id (keyword (p/osan-id osa))
                                                                             :arvo maara-kk
                                                                             :class #{(sarakkeiden-leveys :maara-kk)})
                                                               (p/lisaa-fmt summa-formatointi)))
                                                         (fn [osa]
                                                           (-> osa
                                                               (p/aseta-arvo :id (keyword (p/osan-id osa))
                                                                             :arvo yhteensa
                                                                             :class #{(sarakkeiden-leveys :yhteensa)})
                                                               (p/lisaa-fmt summa-formatointi)))
                                                         (fn [osa]
                                                           (-> osa
                                                               (p/aseta-arvo :id (keyword (p/osan-id osa))
                                                                             :arvo (t/indeksikorjaa (* yhteensa 12))
                                                                             :class #{(sarakkeiden-leveys :indeksikorjattu)
                                                                                      "harmaa-teksti"})
                                                               (p/lisaa-fmt summa-formatointi)))))))
        yhteensa-fn (fn [yhteensa-pohja]
                      (-> yhteensa-pohja
                          (p/aseta-arvo :id :yhteensa
                                        :class #{"table-default" "table-default-sum"})
                          (p/paivita-arvo :lapset
                                          (osien-paivitys-fn (fn [osa]
                                                               (p/aseta-arvo osa
                                                                             :id :yhteensa-nimi
                                                                             :arvo "Yhteensä"
                                                                             :class #{(sarakkeiden-leveys :nimi)}))
                                                             (fn [osa]
                                                               (p/aseta-arvo osa
                                                                             :id :yhteensa-maara-kk
                                                                             :arvo ""
                                                                             :class #{(sarakkeiden-leveys :maara-kk)}))
                                                             (fn [osa]
                                                               (-> osa
                                                                   (p/aseta-arvo :id :yhteensa-yhteensa
                                                                                 :class #{(sarakkeiden-leveys :yhteensa)})
                                                                   (p/lisaa-fmt summa-formatointi)))
                                                             (fn [osa]
                                                               (-> osa
                                                                   (p/aseta-arvo :id :yhteensa-indeksikorjattu
                                                                                 :class #{(sarakkeiden-leveys :indeksikorjattu)
                                                                                          "harmaa-teksti"})
                                                                   (p/lisaa-fmt summa-formatointi)))))))]
    (if on-oikeus?
      (muodosta-taulukko (str rivin-nimi "-taulukko")
                         {:normaali {:janan-tyyppi jana/Rivi
                                     :osat [osa/Teksti
                                            osa/Teksti
                                            osa/Teksti
                                            osa/Teksti]}
                          :syottorivi {:janan-tyyppi jana/Rivi
                                       :osat [osa/Teksti
                                              osa/Syote
                                              osa/Teksti
                                              osa/Teksti]}}
                         ["Nimi" "Määrä" "Yhteensä" "Indeksikorjattu"]
                         [:normaali otsikko-fn :syottorivi syottorivi-fn :normaali yhteensa-fn]
                         {:taulukon-paivitys-fn! taulukon-paivitys-fn!
                          :class #{}
                          :id taulukko-elementin-id})
      (muodosta-taulukko (str rivin-nimi "-taulukko")
                         {:normaali {:janan-tyyppi jana/Rivi
                                     :osat [osa/Teksti
                                            osa/Teksti
                                            osa/Teksti
                                            osa/Teksti]}
                          :rivi {:janan-tyyppi jana/Rivi
                                 :osat [osa/Teksti
                                        osa/Teksti
                                        osa/Teksti
                                        osa/Teksti]}}
                         ["Nimi" "Määrä" "Yhteensä" "Indeksikorjattu"]
                         [:normaali otsikko-fn :rivi rivi-fn :normaali yhteensa-fn]
                         {:taulukon-paivitys-fn! taulukon-paivitys-fn!
                          :class #{}
                          :id taulukko-elementin-id}))))

(defn suunnitellut-hankinnat [e! toimenpiteet valinnat]
  (if toimenpiteet
    [:div
     (for [[toimenpide-avain toimenpide-taulukko] toimenpiteet
           :let [nakyvissa? (= toimenpide-avain (:toimenpide valinnat))]]
       ^{:key toimenpide-avain}
       [p/piirra-taulukko (cond-> toimenpide-taulukko
                                  true (p/paivita-arvo :class (fn [luokat]
                                                                (if nakyvissa?
                                                                  (disj luokat "piillotettu")
                                                                  (conj luokat "piillotettu"))))
                                  nakyvissa? aseta-rivien-nakyvyys
                                  nakyvissa? aseta-rivien-taustavari)])]
    [yleiset/ajax-loader]))

(defn laskutukseen-perustuvat-kustannukset [e! toimenpiteet-laskutukseen-perustuen valinnat]
  (if toimenpiteet-laskutukseen-perustuen
    [:div
     (for [[toimenpide-avain toimenpide-taulukko] toimenpiteet-laskutukseen-perustuen
           :let [nakyvissa? (and (= toimenpide-avain (:toimenpide valinnat))
                                 (contains? (:laskutukseen-perustuen valinnat) toimenpide-avain))]]
       ^{:key toimenpide-avain}
       [p/piirra-taulukko (cond-> toimenpide-taulukko
                                  true (p/paivita-arvo :class (fn [luokat]
                                                                (if nakyvissa?
                                                                  (disj luokat "piillotettu")
                                                                  (conj luokat "piillotettu"))))
                                  nakyvissa? aseta-rivien-nakyvyys
                                  nakyvissa? aseta-rivien-taustavari)])]
    [yleiset/ajax-loader]))

(defn arvioidaanko-laskutukseen-perustuen [_ _ _]
  (let [vaihda-fn (fn [toimenpide event]
                    (let [valittu? (.. event -target -checked)]
                      (e! (tuck-apurit/->PaivitaTila [:suodattimet :hankinnat :laskutukseen-perustuen-valinta]
                                                     (fn [valinnat]
                                                       (if valittu?
                                                         (conj valinnat toimenpide)
                                                         (disj valinnat toimenpide)))))))]
    (fn [{:keys [toimenpide]} laskutukseen-perustuen? on-oikeus?]
      [:div#laskutukseen-perustuen-filter
       [:input#lakutukseen-perustuen.vayla-checkbox
        {:type "checkbox" :checked laskutukseen-perustuen?
         :on-change (partial vaihda-fn toimenpide) :disabled (not on-oikeus?)}]
       [:label {:for "lakutukseen-perustuen"}
        "Haluan suunnitella myös määrämitattavia töitä toimenpiteelle: "
        [:b (-> toimenpide name (clj-str/replace #"-" " ") t/aakkosta clj-str/capitalize)]]])))

(defn suunnitellut-rahavaraukset [e! rahavaraukset valinnat]
  (if rahavaraukset
    [:div
     (for [[toimenpide-avain rahavaraus-taulukko] rahavaraukset
           :let [nakyvissa? (and (= toimenpide-avain (:toimenpide valinnat))
                                 (t/toimenpiteet-rahavarauksilla toimenpide-avain))]]
       ^{:key toimenpide-avain}
       [p/piirra-taulukko (cond-> rahavaraus-taulukko
                                  true (p/paivita-arvo :class (fn [luokat]
                                                                (if nakyvissa?
                                                                  (disj luokat "piillotettu")
                                                                  (conj luokat "piillotettu"))))
                                  nakyvissa? aseta-rivien-nakyvyys
                                  nakyvissa? aseta-rivien-taustavari)])]
    [yleiset/ajax-loader]))

(defn hankintakustannukset-taulukot [e!
                                     {{:keys [yhteenveto toimenpiteet toimenpiteet-laskutukseen-perustuen rahavaraukset] :as kustannukset} :hankintakustannukset
                                      :keys [kuluva-hoitokausi kirjoitusoikeus? indeksit suodatin]}
                                     suunnittellut-hankinnat-grid
                                     laskutukseen-perustuvat-hankinnat-grid
                                     rahavaraukset-grid
                                     kantahaku-valmis?
                                     {:keys [toimenpide laskutukseen-perustuen-valinta] :as suodattimet}]
  (let [suunnitellut-hankinnat-taulukko-valmis? (and suunnittellut-hankinnat-grid kantahaku-valmis?)
        laskutukseen-perustuva-taulukko-valmis? (and laskutukseen-perustuvat-hankinnat-grid kantahaku-valmis?)
        rahavaraukset-taulukko-valmis? (and rahavaraukset-grid kantahaku-valmis?)
        nayta-laskutukseen-perustuva-taulukko? (contains? laskutukseen-perustuen-valinta toimenpide)]
    [:<>
     [:h2#hankintakustannukset "Hankintakustannukset"]
     (if yhteenveto
       ^{:key "hankintakustannusten-yhteenveto"}
       [:div.summa-ja-indeksilaskuri
        [hintalaskuri {:otsikko "Yhteenveto"
                       :selite "Talvihoito + Liikenneympäristön hoito + Sorateiden hoito + Päällystepaikkaukset + MHU Ylläpito + MHU Korvausinvestoiti"
                       :hinnat yhteenveto}
         kuluva-hoitokausi]
        [indeksilaskuri yhteenveto indeksit]]
       ^{:key "hankintakustannusten-loader"}
       [yleiset/ajax-loader "Hankintakustannusten yhteenveto..."])
     [:h3 "Suunnitellut hankinnat"]
     [hankintojen-filter suodattimet]
     (if suunnitellut-hankinnat-taulukko-valmis?
       [grid/piirra suunnittellut-hankinnat-grid]
       [yleiset/ajax-loader])
     [arvioidaanko-laskutukseen-perustuen suodattimet nayta-laskutukseen-perustuva-taulukko? kirjoitusoikeus?]
     (when nayta-laskutukseen-perustuva-taulukko?
       (if laskutukseen-perustuva-taulukko-valmis?
         ^{:key "nayta-lpt"}
         [grid/piirra laskutukseen-perustuvat-hankinnat-grid]
         [yleiset/ajax-loader]))
     #_[suunnitellut-hankinnat e! toimenpiteet valinnat]
     ;; TODO: Korjaa oikeus
     #_[arvioidaanko-laskutukseen-perustuen e! valinnat kirjoitusoikeus?]
     #_[laskutukseen-perustuvat-kustannukset e! toimenpiteet-laskutukseen-perustuen valinnat]
     (when (t/toimenpiteet-rahavarauksilla toimenpide)
         ^{:key "rahavaraukset-otsikko"}
         [:<>
          [:h3 "Rahavarukset"]
          [yleis-suodatin e! suodatin]]
         (if rahavaraukset-taulukko-valmis?
           [grid/piirra rahavaraukset-grid]
           [yleiset/ajax-loader]))
     #_[suunnitellut-rahavaraukset e! rahavaraukset valinnat]]))

(defn jh-toimenkuva-laskulla [jh-laskulla]
  (if jh-laskulla
    [p/piirra-taulukko (-> jh-laskulla
                           (assoc-in [:parametrit :id] "jh-toimenkuva-laskulla")
                           aseta-rivien-nakyvyys
                           aseta-rivien-taustavari)]
    [yleiset/ajax-loader]))

(defn jh-toimenkuva-yhteenveto [jh-yhteenveto]
  (if jh-yhteenveto
    [p/piirra-taulukko (-> jh-yhteenveto
                           (assoc-in [:parametrit :id] "jh-toimenkuva-yhteenveto")
                           aseta-rivien-nakyvyys
                           aseta-rivien-taustavari)]
    [yleiset/ajax-loader]))

(defn maara-kk [taulukko]
  (if taulukko
    [p/piirra-taulukko (-> taulukko
                           aseta-rivien-nakyvyys
                           aseta-rivien-taustavari)]
    [yleiset/ajax-loader]))

(defn erillishankinnat-yhteenveto
  [erillishankinnat menneet-suunnitelmat {:keys [vuosi] :as kuluva-hoitokausi} indeksit]
  (if erillishankinnat
    (let [summarivin-index 1
          tamavuosi-summa (p/arvo (tyokalut/hae-asia-taulukosta erillishankinnat [summarivin-index "Yhteensä"])
                                  :arvo)
          hinnat (map (fn [hoitokausi]
                        (if (>= hoitokausi vuosi)
                          {:summa tamavuosi-summa
                           :hoitokausi hoitokausi}
                          {:summa (* (get-in menneet-suunnitelmat [(dec hoitokausi) :maara-kk]) 12)
                           :hoitokausi hoitokausi}))
                      (range 1 6))]
      [:div.summa-ja-indeksilaskuri
       [hintalaskuri {:otsikko nil
                      :selite "Toimitilat + Kelikeskus- ja keliennustepalvelut + Seurantajärjestelmät"
                      :hinnat hinnat}
        kuluva-hoitokausi]
       [indeksilaskuri hinnat indeksit]])
    [yleiset/ajax-loader]))

(defn erillishankinnat [erillishankinnat]
  [maara-kk erillishankinnat])

(defn erillishankinnat-sisalto [e! erillishankinnat-taulukko menneet-suunnitelmat kuluva-hoitokausi indeksit suodatin]
  [:<>
   [:h3 {:id (:erillishankinnat t/hallinnollisten-idt)} "Erillishankinnat"]
   [erillishankinnat-yhteenveto erillishankinnat-taulukko menneet-suunnitelmat kuluva-hoitokausi indeksit]
   [yleis-suodatin e! suodatin]
   [erillishankinnat erillishankinnat-taulukko]
   [:span "Yhteenlaskettu kk-määrä: Hoitourakan tarvitsemat kelikeskus- ja keliennustepalvelut + Seurantajärjestelmät (mm. ajantasainen seuranta, suolan automaattinen seuranta)"]])

(defn johto-ja-hallintokorvaus-yhteenveto
  [jh-yhteenveto toimistokulut menneet-toimistokulut {:keys [vuosi] :as kuluva-hoitokausi} indeksit]
  (if (and jh-yhteenveto toimistokulut)
    (let [tamavuosi-toimistokulutsumma (p/arvo (tyokalut/hae-asia-taulukosta toimistokulut [1 "Yhteensä"])
                                               :arvo)
          hinnat (map (fn [hoitokausi]
                        {:summa (+ (p/arvo (tyokalut/hae-asia-taulukosta jh-yhteenveto [last (str hoitokausi ".vuosi/€")])
                                           :arvo)
                                   (if (>= hoitokausi vuosi)
                                     tamavuosi-toimistokulutsumma
                                     (* (get-in menneet-toimistokulut [(dec hoitokausi) :maara-kk]) 12)))
                         :hoitokausi hoitokausi})
                      (range 1 6))]
      [:div.summa-ja-indeksilaskuri
       [hintalaskuri {:otsikko nil
                      :selite "Palkat + Toimitilat + Kelikeskus- ja keliennustepalvelut + Seurantajärjestelmät"
                      :hinnat hinnat}
        kuluva-hoitokausi]
       [indeksilaskuri hinnat indeksit]])
    [yleiset/ajax-loader]))

(defn johto-ja-hallintokorvaus [e! jh-laskulla jh-yhteenveto toimistokulut menneet-toimistokulusuunnitelmat kuluva-hoitokausi indeksit suodatin johto-ja-hallintokorvaus-grid johto-ja-hallintokorvaus-yhteenveto-grid kantahaku-valmis?]
  [:<>
   [:h3 {:id (:johto-ja-hallintokorvaus t/hallinnollisten-idt)} "Johto- ja hallintokorvaus"]
   #_[johto-ja-hallintokorvaus-yhteenveto jh-yhteenveto toimistokulut menneet-toimistokulusuunnitelmat kuluva-hoitokausi indeksit]
   [:h3 "Tuntimäärät ja -palkat"]
   #_[yleis-suodatin e! suodatin]
   (if (and johto-ja-hallintokorvaus-grid kantahaku-valmis?)
     [grid/piirra johto-ja-hallintokorvaus-grid]
     [yleiset/ajax-loader])
   (if johto-ja-hallintokorvaus-yhteenveto-grid
     [grid/piirra johto-ja-hallintokorvaus-yhteenveto-grid]
     [yleiset/ajax-loader])
   #_[jh-toimenkuva-yhteenveto jh-yhteenveto]
   [:h3 "Johto ja hallinto: muut kulut"]
   #_[yleis-suodatin e! suodatin]
   #_[maara-kk toimistokulut]
   [:span
    "Yhteenlaskettu kk-määrä: Toimisto- ja ICT-kulut, tiedotus, opastus, kokousten ja vierailujen järjestäminen sekä tarjoilukulut + Hoito- ja korjaustöiden pientarvikevarasto (työkalut, mutterit, lankut, naulat jne.)"]])

(defn hoidonjohtopalkkio-yhteenveto
  [johtopalkkio menneet-suunnitelmat {:keys [vuosi] :as kuluva-hoitokausi} indeksit]
  (if johtopalkkio
    (let [summarivin-index 1
          tamavuosi-summa (p/arvo (tyokalut/hae-asia-taulukosta johtopalkkio [summarivin-index "Yhteensä"])
                                  :arvo)
          hinnat (map (fn [hoitokausi]
                        (if (>= hoitokausi vuosi)
                          {:summa tamavuosi-summa
                           :hoitokausi hoitokausi}
                          {:summa (* (get-in menneet-suunnitelmat [(dec hoitokausi) :maara-kk]) 12)
                           :hoitokausi hoitokausi}))
                      (range 1 6))]
      [:div.summa-ja-indeksilaskuri
       [hintalaskuri {:otsikko nil
                      :selite nil
                      :hinnat hinnat}
        kuluva-hoitokausi]
       [indeksilaskuri hinnat indeksit]])
    [yleiset/ajax-loader]))

(defn hoidonjohtopalkkio [_ _]
  (go (let [g (hoidonjohtopalkkio-grid)]
        (grid/triggeroi-tapahtuma! g :hoidonjotopalkkion-disablerivit)))
  (fn [johtopalkkio kantahaku-valmis?]
    (if (and johtopalkkio kantahaku-valmis?)
      [grid/piirra johtopalkkio]
      [yleiset/ajax-loader])))

(defn hoidonjohtopalkkio-sisalto [e! johtopalkkio menneet-suunnitelmat kuluva-hoitokausi indeksit suodatin johtopalkkio-grid kantahaku-valmis?]
  [:<>
   [:h3 {:id (:hoidonjohtopalkkio t/hallinnollisten-idt)} "Hoidonjohtopalkkio"]
   [hoidonjohtopalkkio-yhteenveto johtopalkkio menneet-suunnitelmat kuluva-hoitokausi indeksit]
   [yleis-suodatin e! suodatin]
   [hoidonjohtopalkkio johtopalkkio-grid kantahaku-valmis?]])

(defn hallinnolliset-toimenpiteet-yhteensa [erillishankinnat jh-yhteenveto johtopalkkio kuluva-hoitokausi indeksit]
  (if (and erillishankinnat jh-yhteenveto johtopalkkio)
    (let [hinnat (map (fn [hoitokausi]
                        (let [eh (p/arvo (tyokalut/hae-asia-taulukosta erillishankinnat [1 "Yhteensä"])
                                         :arvo)
                              jh (p/arvo (tyokalut/hae-asia-taulukosta jh-yhteenveto [last (str hoitokausi ".vuosi/€")])
                                         :arvo)
                              jp (p/arvo (tyokalut/hae-asia-taulukosta johtopalkkio [1 "Yhteensä"])
                                         :arvo)]
                          {:summa (+ eh jh jp)
                           :hoitokausi hoitokausi}))
                      (range 1 6))]
      [:div.summa-ja-indeksilaskuri
       [hintalaskuri {:otsikko "Yhteenveto"
                      :selite "Erillishankinnat + Johto-ja hallintokorvaus + Hoidonjohtopalkkio"
                      :hinnat hinnat}
        kuluva-hoitokausi]
       [indeksilaskuri hinnat indeksit]])
    [yleiset/ajax-loader]))

(defn hallinnolliset-toimenpiteet-sisalto [e!
                                           {{:keys [johto-ja-hallintokorvaus-laskulla johto-ja-hallintokorvaus-yhteenveto
                                                    toimistokulut johtopalkkio erillishankinnat menneet-vuodet]} :hallinnolliset-toimenpiteet
                                            :keys [kuluva-hoitokausi indeksit suodatin]}
                                           johtopalkkio-grid
                                           johto-ja-hallintokorvaus-grid
                                           johto-ja-hallintokorvaus-yhteenveto-grid
                                           kantahaku-valmis?]
  [:<>
   [:h2#hallinnolliset-toimenpiteet "Hallinnolliset toimenpiteet"]
   #_[hallinnolliset-toimenpiteet-yhteensa erillishankinnat johto-ja-hallintokorvaus-yhteenveto johtopalkkio kuluva-hoitokausi indeksit]
   #_[erillishankinnat-sisalto e! erillishankinnat (:erillishankinnat menneet-vuodet) kuluva-hoitokausi indeksit suodatin]
   [johto-ja-hallintokorvaus e! johto-ja-hallintokorvaus-laskulla johto-ja-hallintokorvaus-yhteenveto toimistokulut (:toimistokulut menneet-vuodet) kuluva-hoitokausi indeksit suodatin johto-ja-hallintokorvaus-grid johto-ja-hallintokorvaus-yhteenveto-grid kantahaku-valmis?]
   [hoidonjohtopalkkio-sisalto e! johtopalkkio (:johtopalkkio menneet-vuodet) kuluva-hoitokausi indeksit suodatin johtopalkkio-grid kantahaku-valmis?]])


(defn kustannussuunnitelma*
  [e*! app]
  (komp/luo
    (komp/piirretty (fn [_]
                      (e! (t/->TaulukoidenVakioarvot))
                      (e! (t/->FiltereidenAloitusarvot))
                      (e! (t/->Hoitokausi))
                      (e! (t/->YleisSuodatinArvot))
                      (e! (t/->Oikeudet))
                      (e! (tuck-apurit/->AloitaViivastettyjenEventtienKuuntelu 1000 (:kaskytyskanava app)))
                      (go (let [g-sh (suunnittellut-hankinnat-grid)
                                g-hlp (hankinnat-laskutukseen-perustuen-grid)
                                g-r (rahavarausten-grid)
                                g-jhl (johto-ja-hallintokorvaus-laskulla-grid)
                                g-jhly (johto-ja-hallintokorvaus-laskulla-yhteenveto-grid)]
                            (t/paivita-raidat! (grid/osa-polusta g-sh [::g-pohjat/data]))
                            (t/paivita-raidat! (grid/osa-polusta g-hlp [::g-pohjat/data]))
                            (t/paivita-raidat! (grid/osa-polusta g-jhl [::g-pohjat/data]))
                            (t/paivita-raidat! (grid/osa-polusta g-jhly [::data])))
                          #_(add-watch tila/suunnittelu-kustannussuunnitelma
                                     ::FOOOOOO
                                     (fn [_ _ _ uusi]
                                       (println " ---- FOOO SEURANTA" (get-in uusi [:gridit :rahavaraukset :grid :harja.ui.taulukko.impl.grid/datan-kasittelija])))))
                      (e! (t/->HaeKustannussuunnitelma (partial hankintojen-taulukko e! (:kaskytyskanava app))
                                                       (partial rahavarausten-taulukko e! (:kaskytyskanava app))
                                                       (partial johto-ja-hallintokorvaus-laskulla-taulukko e! (:kaskytyskanava app))
                                                       (partial johto-ja-hallintokorvaus-yhteenveto-taulukko e!)
                                                       (partial maara-kk-taulukko e! (:kaskytyskanava app) [:hallinnolliset-toimenpiteet :erillishankinnat] "Erillishankinnat" "erillishankinnat-taulukko")
                                                       (partial maara-kk-taulukko e! (:kaskytyskanava app) [:hallinnolliset-toimenpiteet :toimistokulut] "Toimistokulut, Pientarvikevarasto" (:toimistokulut-taulukko t/hallinnollisten-idt))
                                                       (partial maara-kk-taulukko e! (:kaskytyskanava app) [:hallinnolliset-toimenpiteet :johtopalkkio] "Hoidonjohtopalkkio" "hoidonjohtopalkkio-taulukko")))))
    (fn [e*! {:keys [kuluva-hoitokausi suodattimet] :as app}]
      (set! e! e*!)
      (let [tavoite-ja-kattohinta-argumentit (select-keys app #{:tavoite-ja-kattohinta :kuluva-hoitokausi :indeksit})
            suunnitelmien-argumentit (select-keys app #{:kaskytyskanava :suunnitelmien-tila-taulukko :suunnitelmien-tila-taulukon-tilat-luotu-kerran? :kirjoitusoikeus? :hankintakustannukset :hallinnolliset-toimenpiteet})
            hankintakustannusten-argumentit (select-keys app #{:hankintakustannukset :kuluva-hoitokausi :kirjoitusoikeus? :indeksit :suodatin})
            hallinnolliset-argumentit (select-keys app #{:hallinnolliset-toimenpiteet :kuluva-hoitokausi :indeksit :suodatin})]
        [:div#kustannussuunnitelma
         ;[debug/debug app]
         [:h1 "Kustannussuunnitelma"]
         [:div "Kun kaikki määrät on syötetty, voit seurata kustannuksia. Sampoa varten muodostetaan automaattisesti maksusuunnitelma, jotka löydät Laskutus-osiosta. Kustannussuunnitelmaa tarkennetaan joka hoitovuoden alussa."]
         [kuluva-hoitovuosi kuluva-hoitokausi]
         #_[haitari-laatikko
            "Tavoite- ja kattohinta lasketaan automaattisesti"
            {:alussa-auki? true
             :id "tavoite-ja-kattohinta"}
            [tavoite-ja-kattohinta-sisalto tavoite-ja-kattohinta-argumentit]
            [:span#tavoite-ja-kattohinta-huomio
             "*) Vuodet ovat hoitovuosia, ei kalenterivuosia."]]
         [:span.viiva-alas]
         #_[haitari-laatikko
            "Suunnitelmien tila"
            {:alussa-auki? true
             :otsikko-elementti :h2}
            [suunnitelmien-tila e! suunnitelmien-argumentit]]
         [:span.viiva-alas]
         #_(println "VALINNAT: " (get app :valinnat))
         [hankintakustannukset-taulukot e! hankintakustannusten-argumentit
          (get-in app [:gridit :suunnittellut-hankinnat :grid])
          (get-in app [:gridit :laskutukseen-perustuvat-hankinnat :grid])
          (get-in app [:gridit :rahavaraukset :grid])
          (:kantahaku-valmis? app) (:hankinnat suodattimet)]
         [:span.viiva-alas]
         [hallinnolliset-toimenpiteet-sisalto e! hallinnolliset-argumentit (get-in app [:gridit :hoidonjohtopalkkio :grid]) (get-in app [:gridit :johto-ja-hallintokorvaus :grid]) (get-in app [:gridit :johto-ja-hallintokorvaus-yhteenveto :grid]) (:kantahaku-valmis? app)]]))))

(defn kustannussuunnitelma []
  [tuck/tuck tila/suunnittelu-kustannussuunnitelma kustannussuunnitelma*])

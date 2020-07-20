(ns harja.ui.taulukko
  (:require [harja.ui.taulukko.grid-pohjat :as g-pohjat]
            [harja.ui.taulukko.impl.solu :as solu]
            [harja.ui.taulukko.grid :as grid]
            [harja.ui.taulukko.grid-oletusarvoja :as konf]
            [harja.ui.grid-debug :as g-debug]
            [harja.loki :refer [log warn error]]
            [harja.fmt :as fmt]
            [clojure.string :as clj-str]
            [clojure.set :as clj-set]
            [cljs.spec.alpha :as s]
            [clojure.walk :as walk]
            [taoensso.timbre :as log])
  (:require-macros [harja.ui.taulukko.grid :refer [jarjesta-data triggeroi-seurannat]]))

(def ^:dynamic *vaihto-osien-mappaus* nil)
(def ^:dynamic *otsikon-nimi* nil)
(def ^:dynamic *osamaaritelmien-polut* nil)

(defn summa-formatointi [teksti]
  (if (nil? teksti)
    "0,00"
    (let [teksti (clj-str/replace (str teksti) "," ".")]
      (if (or (= "" teksti) (js/isNaN teksti))
        "0,00"
        (fmt/desimaaliluku teksti 2 true)))))

(defn summa-formatointi-aktiivinen [teksti]
  (let [teksti-ilman-pilkkua (clj-str/replace (str teksti) "," ".")]
    (cond
      (or (nil? teksti) (= "" teksti)) ""
      (re-matches #".*\.0*$" teksti-ilman-pilkkua) (apply str (fmt/desimaaliluku teksti-ilman-pilkkua nil true)
                                                          (drop 1 (re-find #".*(\.|,)(0*)" teksti)))
      :else (fmt/desimaaliluku teksti-ilman-pilkkua nil true))))

(defn poista-tyhjat [arvo]
  (clj-str/replace arvo #"\s" ""))

(defn paivita-raidat! [g vaihdetun-osan-nimet]
  (let [paivita-luokat (fn [luokat odd?]
                         (if odd?
                           (-> luokat
                               (conj "table-default-odd")
                               (disj "table-default-even"))
                           (-> luokat
                               (conj "table-default-even")
                               (disj "table-default-odd"))))]
    (loop [[rivi & loput-rivit] (grid/nakyvat-rivit g)
           index 0]
      (if rivi
        (let [rivin-nimi (grid/hae-osa rivi :nimi)]
          (grid/paivita-grid! rivi
                              :parametrit
                              (fn [parametrit]
                                (update parametrit :class (fn [luokat]
                                                            (if (contains? vaihdetun-osan-nimet rivin-nimi)
                                                              (paivita-luokat luokat (not (odd? index)))
                                                              (paivita-luokat luokat (odd? index)))))))
          (recur loput-rivit
                 (if (contains? vaihdetun-osan-nimet rivin-nimi)
                   index
                   (inc index))))))))

(defn paivita-solun-arvo! [{:keys [paivitettava-asia arvo solu ajettavat-jarejestykset triggeroi-seuranta?]
                            :or {ajettavat-jarejestykset false triggeroi-seuranta? false}}]
  (jarjesta-data ajettavat-jarejestykset
    (triggeroi-seurannat triggeroi-seuranta?
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

(defmulti predef
          (fn [optio _]
            optio))

(defmethod predef :numero
  [_ {:keys [desimaalien-maara positiivinen-arvo?]
      :or {desimaalien-maara 2
           positiivinen-arvo? true}}]
  (let [kaytos (if positiivinen-arvo?
                 :positiivinen-numero
                 :numero)]
    {:fmt summa-formatointi
     :fmt-aktiivinen summa-formatointi-aktiivinen
     :kayttaytymiset {:on-change [{kaytos {:desimaalien-maara desimaalien-maara}}
                                  {:eventin-arvo {:f poista-tyhjat}}]
                      :on-blur [kaytos
                                {:eventin-arvo {:f poista-tyhjat}}]}}))

(defmethod predef :input
  [_ _]
  {:toiminnot {:on-change (fn [arvo]
                            (when arvo
                              (paivita-solun-arvo! {:paivitettava-asia :aseta-arvo!
                                                    :arvo arvo
                                                    :solu solu/*this*})))
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
   :parametrit {:size 2
                :class #{"input-default"}}})

(defmethod predef :syote-tayta-alas
  [_ conf]
  {:nappia-painettu! (fn [rivit-alla arvo]
                                (let [grid (grid/root (first rivit-alla))]
                                  (tayta-alla-olevat-rivit! :aseta-arvo! rivit-alla arvo)
                                  (paivita-solun-arvo! {:paivitettava-asia :aseta-arvo!
                                                        :arvo arvo
                                                        :solu solu/*this*
                                                        :ajettavat-jarejestykset :deep
                                                        :triggeroi-seuranta? true})
                                  (grid/jarjesta-grid-data! grid)))
   :on-focus (fn [_]
               (grid/paivita-osa! solu/*this*
                                  (fn [solu]
                                    (assoc solu :nappi-nakyvilla? true))))})

(defn laajenna-solua-klikattu [laajennasolu vaihdetun-osan-nimet aukeamispolku sulkemispolku auki?]
  (let [g (grid/root laajennasolu)]
    (if auki?
      (do (grid/nayta! (grid/osa-polusta laajennasolu aukeamispolku))
          (paivita-raidat! g vaihdetun-osan-nimet))
      (do (grid/piillota! (grid/osa-polusta laajennasolu sulkemispolku))
          (paivita-raidat! g vaihdetun-osan-nimet)))))

(defmethod predef :laajenna
  [_ {:keys [aukeamispolku sulkemispolku]}]
  {:aukaise-fn (with-meta (fn []
                            (let [vaihdetun-osan-nimet (->> *vaihto-osien-mappaus* deref :vaihto-osat keys (into #{}))]
                              (fn [this auki?]
                                (laajenna-solua-klikattu this vaihdetun-osan-nimet aukeamispolku sulkemispolku auki?))))
                          {:aja-taulukon-luontivaiheessa? true})
   :auki-alussa? false})

#_(defn predefs [{:keys [conf predefs solu] :as args}]
  (let [solu-args (reduce (predef optio (get args
                                             (keyword (str (name optio) "-conf"))))
                          {}
                          predefs)]))

(defn poista-nil
  [osio m]
  {:pre [(map? m)
         (#{:key :val} osio)]
   :post [(map? m)]}
  (reduce-kv (fn [m k v]
               (cond
                 (and (nil? k) (= :key osio)) m
                 (and (nil? v) (= :val osio)) m
                 :else (assoc m k v)))
             {}
             m))

(defn- taulukon-koko-conf [taulukko]
  (let [taulukon-osiot (select-keys taulukko #{:header :body :footer})
        jarjestys {:header 0 :body 1 :footer 2}
        osioiden-index (poista-nil :key
                                   (loop [[[osion-nimi _] & loput] (sort-by key
                                                                            (comparator (fn [a b]
                                                                                          (< (jarjestys a) (jarjestys b))))
                                                                            taulukon-osiot)
                                          i 0
                                          nimet {}]
                                     (if (nil? osion-nimi)
                                       nimet
                                       (recur loput
                                              (inc i)
                                              (assoc nimet osion-nimi i)))))]
    {:rivi-n (+ (count taulukon-osiot)
                (if-let [body (:body taulukon-osiot)]
                  (dec (count body))
                  0))
     :nimet-osioihin (poista-nil :val
                                 (clj-set/rename-keys osioiden-index
                                                      {:header (get-in taulukon-osiot [:header :conf :nimi])
                                                       :body (get-in taulukon-osiot [:body :conf :nimi])
                                                       :footer (get-in taulukon-osiot [:footer :conf :nimi])}))
     :header-index (:header osioiden-index)
     :body-index (:body osioiden-index)
     :footer-index (:footer osioiden-index)}))

(defn vaihda-osa-takaisin! [osa vaihdettavan-osan-polku]
  (when g-debug/GRID_DEBUG
    (when (and (nil? osa)
               (nil? solu/*this*))
      (error (str "Yritettiin vaihtaa osaa, mutta annettu solu on nil ja harja.ui.taulukko.impl.solu/*this* on myös nil!\n\n"
                  "Korjaa virhe antamalla vaihda-osa! funktiolle osa, josta root voidaan hakea tai toteuta tämän funktion triggeröimä"
                  " solu siten, että harja.ui.taulukko.impl.solu/*this* sisältää tämän solun."))))
  (let [root-grid (if (nil? osa)
                    (grid/root solu/*this*)
                    (grid/root osa))
        vaihdettava-osa (grid/get-in-grid root-grid vaihdettavan-osan-polku)
        vaihdettavan-osan-id (grid/hae-osa vaihdettava-osa :id)
        vaihto-osien-mappaus (get root-grid ::vaihto-osien-mappaus)
        vanha-osa (get-in @vaihto-osien-mappaus [:vaihdettu vaihdettavan-osan-id])]
    (swap! vaihto-osien-mappaus update :vaihdettu dissoc vaihdettavan-osan-id)
    (grid/vaihda-osa!
      vaihdettava-osa
      (constantly vanha-osa))))

(defn vaihda-osa! [osa vaihdettavan-osan-polku]
  (when g-debug/GRID_DEBUG
    (when (and (nil? osa)
               (nil? solu/*this*))
      (error (str "Yritettiin vaihtaa osaa, mutta annettu solu on nil ja harja.ui.taulukko.impl.solu/*this* on myös nil!\n\n"
                  "Korjaa virhe antamalla vaihda-osa! funktiolle osa, josta root voidaan hakea tai toteuta tämän funktion triggeröimä"
                  " solu siten, että harja.ui.taulukko.impl.solu/*this* sisältää tämän solun."))))
  (let [root-grid (if (nil? osa)
                    (grid/root solu/*this*)
                    (grid/root osa))
        vaihdettava-osa (grid/get-in-grid root-grid vaihdettavan-osan-polku)
        vaihdettavan-osan-id (grid/hae-osa vaihdettava-osa :id)
        vaihto-osien-mappaus (get root-grid ::vaihto-osien-mappaus)
        vaihto-osan-tunniste (get-in @vaihto-osien-mappaus [:mappaus vaihdettavan-osan-id])
        uusi-osa (grid/samanlainen-osa (get-in @vaihto-osien-mappaus [:vaihto-osat vaihto-osan-tunniste]))]
    (swap! vaihto-osien-mappaus update-in [:vaihdettu (grid/hae-osa uusi-osa :id)] vaihdettava-osa)
    (grid/vaihda-osa!
      vaihdettava-osa
      (constantly uusi-osa))))

(declare muodosta-grid-rivi muodosta-grid-staattinen-taulukko muodosta-grid-dynaaminen-taulukko #_muodosta-grid-solu)

(defn vaihdettavan-osan-ilmoitus! [osan-maaritelma osan-id]
  (when-let [vaihdettavan-osan-nimi (get-in osan-maaritelma [:conf :vaihdettava-osa])]
    (when g-debug/GRID_DEBUG
      (when (nil? *vaihto-osien-mappaus*)
        (warn "*vaihto-osien-mappaus* on nil vaikka siihen yritetään asettaa vaihdettava-osa!")))
    (swap! *vaihto-osien-mappaus*
           (fn [mappaukset]
             (update mappaukset :mappaus assoc osan-id vaihdettavan-osan-nimi)))))

(defn- staattinen-taulukko? [osan-maaritelma]
  (not (empty? (select-keys osan-maaritelma #{:header :body :footer}))))

(defn- dynaaminen-taulukko? [osan-maaritelma]
  (get osan-maaritelma :toistettava-osa))

(defn- rivi? [osan-maaritelma]
  (contains? osan-maaritelma :osat))

(defn- solu? [osan-maaritelma]
  (grid/solu? osan-maaritelma))

(defn- solu-conf? [osan-maaritelma]
  (contains? osan-maaritelma :solu))

(defn- muodosta-grid-osa!
  [osan-maaritelma]
  (let [muodostettu-osa (cond
                          (staattinen-taulukko? osan-maaritelma) (muodosta-grid-staattinen-taulukko osan-maaritelma)
                          (dynaaminen-taulukko? osan-maaritelma) (muodosta-grid-dynaaminen-taulukko osan-maaritelma)
                          (rivi? osan-maaritelma) (muodosta-grid-rivi osan-maaritelma)
                          (solu? osan-maaritelma) osan-maaritelma
                          (solu-conf? osan-maaritelma) (:solu osan-maaritelma) #_(muodosta-grid-solu osa))
        muodostettu-osa (walk/prewalk (fn [x]
                                        (if (and (map-entry? x)
                                                 (fn? (second x))
                                                 (-> x second meta :aja-taulukon-luontivaiheessa?))
                                          (update x 1 (fn [f] (f)))
                                          x))
                                      muodostettu-osa)]
    (vaihdettavan-osan-ilmoitus! osan-maaritelma (grid/hae-osa muodostettu-osa :id))
    muodostettu-osa))

#_(defn- muodosta-grid-solu [solumaaritelma]
  (let [{:keys [solu]}]))

(defn- muodosta-grid-rivi [rivimaaritelma]
  (let [{:keys [nimi koko luokat]} (get rivimaaritelma :conf)]
    (grid/rivi {:nimi (or nimi (str (gensym)))
                :koko (or koko
                          (and *otsikon-nimi*
                               {:seuraa {:seurattava *otsikon-nimi*
                                         :sarakkeet :sama
                                         :rivit :sama}})
                          konf/livi-oletuskoko)
                :osat (mapv muodosta-grid-osa! (:osat rivimaaritelma))
                :luokat (clj-set/union #{"salli-ylipiirtaminen"} luokat)}
               [{:sarakkeet [0 (count (:osat rivimaaritelma))] :rivit [0 1]}])))

(defn- muodosta-grid-dynaaminen-taulukko [taulukkomaaritelma]
  (let [{:keys [nimi luokat]} (:conf taulukkomaaritelma)
        vaihdetun-osan-nimet (->> *vaihto-osien-mappaus* deref :vaihto-osat keys (into #{}))
        toistettava-osa (muodosta-grid-osa! (:toistettava-osa taulukkomaaritelma))]
    (grid/dynamic-grid {:nimi (or nimi ::data)
                        :alueet [{:sarakkeet [0 1] :rivit [0 1]}]
                        :osatunnisteet #(map key %)
                        :koko konf/auto
                        :luokat (clj-set/union #{"salli-ylipiirtaminen"} luokat)
                        :osien-maara-muuttui! (fn [g _]
                                                (paivita-raidat! g vaihdetun-osan-nimet))
                        :toistettavan-osan-data identity
                        :toistettava-osa (fn [rivien-data]
                                           (mapv (fn [rivin-data]
                                                   toistettava-osa)
                                                 rivien-data))})))

(defn- muodosta-grid-staattinen-taulukko
  ([taulukkomaaritelma] (muodosta-grid-staattinen-taulukko taulukkomaaritelma nil))
  ([taulukkomaaritelma root-conf]
   (let [{:keys [rivi-n nimet-osioihin header-index body-index footer-index]} (taulukon-koko-conf taulukkomaaritelma)
         {:keys [nimi koko]} (get taulukkomaaritelma :conf)
         header-osa (when header-index
                      (muodosta-grid-osa! (:header taulukkomaaritelma)))
         headerosa-koko-maarityksella (first
                                        (grid/hae-kaikki-osat header-osa
                                                              (every-pred grid/rivi?
                                                                          #(boolean (grid/hae-osa % :nimi))
                                                                          #(nil? (get (grid/hae-grid % :koko) :seurattava)))))]
     (println "HEADEROSA KOKO MÄÄRITYKSELLÄ: " headerosa-koko-maarityksella)
     (when headerosa-koko-maarityksella
       (println "nimi " (grid/hae-osa headerosa-koko-maarityksella :nimi)))
     (binding [*otsikon-nimi* (if headerosa-koko-maarityksella
                                (grid/hae-osa headerosa-koko-maarityksella :nimi)
                                *otsikon-nimi*)]
       (grid/grid (merge {:nimi nimi
                          :alueet [{:sarakkeet [0 1] :rivit [0 rivi-n]}]
                          :koko (or koko
                                    (cond-> konf/auto
                                            nimet-osioihin (assoc-in [:rivi :nimet]
                                                                     nimet-osioihin)
                                            header-index (assoc-in [:rivi :korkeudet header-index] "40px")
                                            footer-index (assoc-in [:rivi :korkeudet footer-index] "40px")))
                          :osat (vec
                                  (cond-> []
                                          header-osa (conj header-osa)
                                          body-index (concat (map muodosta-grid-osa! (:body taulukkomaaritelma)))
                                          footer-index (concat [(muodosta-grid-osa! (:footer taulukkomaaritelma))])))}
                         root-conf))))))

(defn muodosta-vaihto-osat! [vaihto-osien-mappaus vaihto-osat]
  (swap! vaihto-osien-mappaus
         (fn [mappaukset]
           (assoc mappaukset
                  :vaihto-osat
                  (reduce-kv (fn [m osan-tunniste osan-maaritelma]
                               (assoc m osan-tunniste (muodosta-grid-osa! osan-maaritelma)))
                             {}
                             vaihto-osat)))))

(defn- muodosta-grid [{:keys [ratom dom-id grid-polku]} taulukkomaaritelma]
  (let [root-conf {:dom-id dom-id
                   :root-fn (fn [] (get-in @ratom grid-polku))
                   :paivita-root! (fn [f]
                                    (swap! ratom
                                           (fn [tila]
                                             (update-in tila grid-polku f))))}]
    (muodosta-grid-staattinen-taulukko taulukkomaaritelma
                                       root-conf)))

(declare muodosta-datakasittelija-ratom-rivi muodosta-datakasittelija-ratom-staattinen-taulukko muodosta-datakasittelija-ratom-dynaaminen-taulukko)

#_(defn uusi-datapolku
  ([entinen-polku osamaaritelma seuraavan-osan-osamaaritelma] (uusi-datapolku entinen-polku osamaaritelma seuraavan-osan-osamaaritelma nil))
  ([entinen-polku osamaaritelma seuraavan-osan-osamaaritelma staattisen-taulukon-osa]
   (let [staattinen-grid? (staattinen-taulukko? osamaaritelma)
         tama-grid? (or staattinen-grid?
                        (dynaaminen-taulukko? osamaaritelma))
         seuraava-grid? (or (staattinen-taulukko? seuraavan-osan-osamaaritelma)
                            (dynaaminen-taulukko? seuraavan-osan-osamaaritelma))
         rivi? (rivi? osamaaritelma)]
     (cond
       (and staattinen-grid? staattisen-taulukon-osa) (conj entinen-polku (get-in seuraavan-osan-osamaaritelma [:conf :nimi]))
       (and tama-grid? seuraava-grid?) (conj entinen-polku )
       rivi? entinen-polku)))
  (conj entinen-polku (if (get-in osamaaritelma [:conf ::nimi-generoitu?])
                        vaihtoehtonimi
                        (get-in osamaaritelma [:conf :nimi]))))

(defn uusi-datapolku
  [entinen-polku root-osa? taman-osan-index taman-osan-nimi]
  (cond
    root-osa? entinen-polku
    taman-osan-nimi (conj entinen-polku taman-osan-nimi)
    :else (conj entinen-polku taman-osan-index)))

{:otsikot {:nimi "rivien nimet" :a 1 :b 2}
 :data {:foo [{:a 1 :b 2}
              {:a 1 :b 2}
              {:a 1 :b 2}]
        :bar [{:a 3 :b 4}
              {:a 3 :b 4}]}}

(defn uusi-gridpolku [entinen-polku root-osa? taman-osan-index taman-osan-nimi]
  (cond
    #_#_(and entinen-staattinen-taulukko? (integer? (last entinen-polku))) entinen-polku
    root-osa? entinen-polku
    taman-osan-nimi (conj entinen-polku taman-osan-nimi)
    :else (conj entinen-polku taman-osan-index)))

(defn muodosta-rajapinnan-nimi-polusta [polku]
  (apply str (interpose "-" (map #(if (keyword? %) (name %) (str %)) polku))))

(defn yksiloivan-datan-generointi [data yksiloivakentta]
  ;; Data on vektori mappeja
  (let [max-yksiloiva-tunnistin (apply max (conj (map yksiloivakentta data) -1))]
    (if (= -1 max-yksiloiva-tunnistin)
      (vec (map-indexed (fn [index m]
                          (assoc m yksiloivakentta index))
                        data))
      (loop [[datapoint & loput] data
             seuraava-tunnistin (inc max-yksiloiva-tunnistin)
             data-tunnistimilla []]
        (if (nil? datapoint)
          data-tunnistimilla
          (let [datapointilla-tunnistin? (get datapoint yksiloivakentta)]
            (recur loput
                   (if datapointilla-tunnistin?
                     seuraava-tunnistin
                     (inc seuraava-tunnistin))
                   (if datapointilla-tunnistin?
                     (conj data-tunnistimilla datapoint)
                     (conj data-tunnistimilla (assoc datapoint yksiloivakentta seuraava-tunnistin))))))))))

(defmulti muodosta-datakasittelija-ratom-maaritelma
          (fn [osamaaritelma]
            (let [osamaaritelman-id (get-in osamaaritelma [:conf ::id])
                  osapolku (get-in *osamaaritelmien-polut* [osamaaritelman-id :osapolku])
                  dynaamisen-sisalla? (some #(= % :dynaaminen-taulukko) (butlast osapolku))]
              (cond
                dynaamisen-sisalla? :dynaamisen-sisalla
                (staattinen-taulukko? osamaaritelma) :staattinen-taulukko
                (dynaaminen-taulukko? osamaaritelma) :dynaaminen-taulukko
                (rivi? osamaaritelma) :rivi
                :else :default))))

(defmethod muodosta-datakasittelija-ratom-maaritelma :staattinen-taulukko
  [osamaaritelma]
  {:polut [(get-in *osamaaritelmien-polut* [(get-in osamaaritelma [:conf ::id]) :datapolku])]
   :haku identity})

(defmethod muodosta-datakasittelija-ratom-maaritelma :dynaaminen-taulukko
  [taulukkomaaritelma]
  (let [{:keys [yksiloivakentta]
         generoi-yksiloivakentta? ::generoi-yksiloivakentta?} taulukkomaaritelma
        haettavan-datan-polku (get-in *osamaaritelmien-polut* [(get-in taulukkomaaritelma [:conf ::id]) :datapolku])]
    {:polut [haettavan-datan-polku]
     :luonti-init (fn [tila _]
                    (if generoi-yksiloivakentta?
                      (update-in tila haettavan-datan-polku yksiloivan-datan-generointi yksiloivakentta)
                      tila))
     :haku identity
     :identiteetti {1 key}}))

(defmethod muodosta-datakasittelija-ratom-maaritelma :rivi
  [osamaaritelma]
  {:polut [(get-in *osamaaritelmien-polut* [(get-in osamaaritelma [:conf ::id]) :datapolku])]
   :haku identity})

(defn foo-bar
  ([{yk :yksiloivakentta :as osamaaritelma} haettavan-datan-polku osan-tyypit data gridpolku]
   (vec
     (map-indexed (fn [index datapoint]
                    (let [haettavan-datan-polku (vec (conj haettavan-datan-polku index))]
                      (foo-bar (or (get osamaaritelma :toistettava-osa)
                                   (get osamaaritelma :body)
                                   (get osamaaritelma :osat))
                               haettavan-datan-polku
                               (rest osan-tyypit)
                               datapoint
                               gridpolku
                               {(str (muodosta-rajapinnan-nimi-polusta gridpolku) "-" yk) haettavan-datan-polku})))
                  data)))
  ([{yk :yksiloivakentta :as osamaaritelma} haettavan-datan-polku [osan-tyyppi & loput] data gridpolku hakupolut]
   (case osan-tyyppi
     (::dynaaminen ::staattinen) (vec
                                   (mapcat (fn [datapoint]
                                             (foo-bar (or (get osamaaritelma :toistettava-osa)
                                                          (get osamaaritelma :body)
                                                          (get osamaaritelma :osat))
                                                      haettavan-datan-polku
                                                      loput
                                                      datapoint
                                                      (reduce (fn [hakupolut hakupolku]
                                                                (let [[nimi polut] (first hakupolku)]
                                                                  (vec
                                                                    (concat hakupolut
                                                                            (map-indexed (fn [index _]
                                                                                           {(str nimi "-" yk) [(if (= ::staattinen osan-tyyppi)
                                                                                                                 (conj (first polut) :body index)
                                                                                                                 (conj (first polut) index))]})
                                                                                         data)))))
                                                              []
                                                              hakupolut)))
                                           data))
     ::rivi hakupolut
     hakupolut)))

(defmethod muodosta-datakasittelija-ratom-maaritelma :dynaamisen-sisalla
  [osamaaritelma]
  [:root :body 0 ::dynaaminen 0 :body 0 ::dynaaminen 0]
  [::staattinen ::dynaaminen ::staattinen ::dynaaminen]
  (let [osamaaritelman-id (get-in osamaaritelma [:conf ::id])
        haettavan-datan-polku (get-in *osamaaritelmien-polut* [osamaaritelman-id :datapolku])
        osapolku (get-in *osamaaritelmien-polut* [osamaaritelman-id :osapolku])
        gridpolku (get-in *osamaaritelmien-polut* [osamaaritelman-id :gridpolku])]
    {:polut [haettavan-datan-polku]
     :luonti (fn [data]
               (foo-bar osamaaritelma
                        haettavan-datan-polku
                        osapolku
                        data
                        gridpolku)
               )
     :haku identity}))

(defmethod muodosta-datakasittelija-ratom-maaritelma :default
  [osamaaritelma]
  (log/debug "foo"))

(defn muodosta-datakasittelija-ratom
  ([osamaaritelma] (muodosta-datakasittelija-ratom osamaaritelma {}))
  ([osamaaritelma datakasittely]
   (let [staattinen-taulukko? (staattinen-taulukko? osamaaritelma)
         dynaaminen-taulukko? (dynaaminen-taulukko? osamaaritelma)
         rivi? (rivi? osamaaritelma)]
     (if-not (or staattinen-taulukko? dynaaminen-taulukko? rivi?)
       datakasittely
       (let [staattisen-gridin-uusi-datakasittelija (fn [datakasittely osan-maarittelman-nimi]
                                                      (merge datakasittely
                                                             (muodosta-datakasittelija-ratom (get osamaaritelma osan-maarittelman-nimi)
                                                                                             datakasittely)))
             stattisen-gridin-datakasittelija (fn [datakasittely]
                                                (second (reduce (fn [[index datakasittely] seuraava-osamaaritelma]
                                                                  [(inc index)
                                                                   (merge datakasittely
                                                                          (muodosta-datakasittelija-ratom seuraava-osamaaritelma
                                                                                                          datakasittely))])
                                                                [0 datakasittely]
                                                                (:body osamaaritelma))))
             uusi-datakasittely (assoc datakasittely
                                       (muodosta-rajapinnan-nimi-polusta (get-in *osamaaritelmien-polut* [(get-in osamaaritelma [:conf ::id]) :datapolku]))
                                       (muodosta-datakasittelija-ratom-maaritelma osamaaritelma))]
         (cond
           staattinen-taulukko? (cond-> uusi-datakasittely
                                        (:header osamaaritelma) (staattisen-gridin-uusi-datakasittelija :header)
                                        (:body osamaaritelma) stattisen-gridin-datakasittelija
                                        (:footer osamaaritelma) (staattisen-gridin-uusi-datakasittelija :footer))
           dynaaminen-taulukko? (muodosta-datakasittelija-ratom (get osamaaritelma :toistettava-osa) uusi-datakasittely)
           rivi? (muodosta-datakasittelija-ratom (get osamaaritelma :osat) uusi-datakasittely)))))))

(defn tayta-taulukkomaaritelma [taulukkomaaritelma]
  (letfn [(tarkista-staattisen-taulukon-nimet
            [x]
            (let [staattinen-taulukko? (and (map? x)
                                            (staattinen-taulukko? x))]
              (if staattinen-taulukko?
                (let [header-puuttuu-nimi? (and (:header x) (nil? (get-in x [:header :conf :name])))
                      footer-puuttuu-nimi? (and (:footer x) (nil? (get-in x [:footer :conf :name])))]
                  (cond-> x
                          header-puuttuu-nimi? (assoc-in [:header :conf :name] :header)
                          footer-puuttuu-nimi? (assoc-in [:footer :conf :name] :footer)))
                x)))
          (tarkista-osan-nimi
            [x]
            (if (and (map? x)
                     (nil? (get-in x [:conf :nimi])))
              (cond
                (staattinen-taulukko? x) (assoc-in x [:conf :nimi] :body)
                (dynaaminen-taulukko? x) (assoc-in x [:conf :nimi] :dynaaminen)
                (rivi? x) (assoc-in x [:conf :nimi] :rivi)
                :else x)
              x))
          ;Lisää nimet kaikkiin muihin paitsi soluihin
          (lisaa-nimet
            [taulukkomaaritelma]
            (walk/prewalk #(-> % tarkista-staattisen-taulukon-nimet tarkista-osan-nimi)
                          taulukkomaaritelma))
          ;Lisää dynaamisille taulukoille yksilöivän tunnisteen
          (lisaa-yksiloivat-tiedot
            [taulukkomaaritelma]
            (walk/prewalk (fn [x]
                            (if (and (map? x)
                                     (dynaaminen-taulukko? x)
                                     (not (get-in x [:conf :yksiloivakentta])))
                              (-> x
                                  (assoc-in [:conf :yksiloivakentta] (keyword (str (gensym "id"))))
                                  (assoc-in [:conf ::generoi-yksiloivakentta?] true))
                              x))
                          taulukkomaaritelma))
          ;Lisää confkentille idt
          (lisaa-idt
            [taulukkomaaritelma]
            (walk/prewalk (fn [x]
                            (if (and (map? x)
                                     (or (staattinen-taulukko? x)
                                         (dynaaminen-taulukko? x)
                                         (rivi? x)))
                              (assoc-in x [:conf ::id] (gensym "id"))
                              x))
                          taulukkomaaritelma))]
    (-> taulukkomaaritelma
        lisaa-nimet
        lisaa-yksiloivat-tiedot
        lisaa-idt)))

(declare muodosta-rajapintakasittelija-taulukko)

(defn- dynaamisen-osan-rajapintakasittelijat [osamaaritelma]
  (reduce-kv (fn [m k v]
               (assoc m
                      (fn [index]
                        (vec (concat [:. index] k)))
                      v))
             {}
             (muodosta-rajapintakasittelija-taulukko osamaaritelma true)))

(defn- muodosta-rajapintakasittelija-dynaaminen-osa [osamaaritelma]
  (let [rajapintakasittelijat (dynaamisen-osan-rajapintakasittelijat osamaaritelma)]
    (fn [g index]
      (reduce-kv (fn [m k v]
                   (let [polku (k index)]

                     (when (grid/get-in-grid g (vec (rest polku)))
                       (assoc m polku v))))
                 {}
                 rajapintakasittelijat))))

(defmulti muodosta-rajapintakasittelija-taulukko-maaritelma
          (fn [osamaaritelma _]
            (cond
              (dynaaminen-taulukko? osamaaritelma) :dynaaminen-taulukko
              (rivi? osamaaritelma) :rivi
              :else :default)))

(defmethod muodosta-rajapintakasittelija-taulukko-maaritelma :dynaaminen-taulukko
  [taulukkomaaritelma rajapinnan-nimi]
  (let [jarjestys (get-in taulukkomaaritelma [:conf :jarjestys])]
    {:rajapinta rajapinnan-nimi
     :solun-polun-pituus 0
     :jarjestys jarjestys
     :datan-kasittely identity
     :luonti (let [dynaamisen-sisus (muodosta-rajapintakasittelija-dynaaminen-osa (:toistettava-osa taulukkomaaritelma))]
               (fn [data g]
                 (println "DYNAAMINEN SISUS: ")
                 (println (map-indexed (fn [index datapoint]
                                         (let [{:keys [yksiloivakentta]
                                                generoi-yksiloivakentta? ::generoi-yksiloivakentta?} taulukkomaaritelma
                                               {yk yksiloivakentta} datapoint
                                               ]
                                           (dynaamisen-sisus g index)))
                                       data))
                 (map-indexed (fn [index datapoint]
                                (let [{:keys [yksiloivakentta]
                                       generoi-yksiloivakentta? ::generoi-yksiloivakentta?} taulukkomaaritelma
                                      {yk yksiloivakentta} datapoint
                                      ]
                                  (dynaamisen-sisus g index)))
                              data)))}))

(defmethod muodosta-rajapintakasittelija-taulukko-maaritelma :rivi
  [osamaaritelma rajapinnan-nimi]
  (let [jarjestys (get-in osamaaritelma [:conf :jarjestys])]
    {:rajapinta rajapinnan-nimi
     :solun-polun-pituus 1
     :jarjestys jarjestys
     :datan-kasittely (fn [rivin-data]
                        (println "RIVIN DATA RAJAPINNALLE - " rajapinnan-nimi ": " rivin-data)
                        (vec (vals rivin-data)))}))

(defmethod muodosta-rajapintakasittelija-taulukko-maaritelma :default
  [osamaaritelma rajapinnan-nimi]
  (log/debug "foo"))

(defn muodosta-rajapintakasittelija-taulukko
  ([osamaaritelma] (muodosta-rajapintakasittelija-taulukko osamaaritelma false {}))
  ([osamaaritelma vaihto-osat-mukaan?] (muodosta-rajapintakasittelija-taulukko osamaaritelma vaihto-osat-mukaan? {}))
  ([osamaaritelma vaihto-osat-mukaan? rajapinnankasittely]
   (let [staattinen-taulukko? (staattinen-taulukko? osamaaritelma)
         dynaaminen-taulukko? (dynaaminen-taulukko? osamaaritelma)
         rivi? (rivi? osamaaritelma)]
     (if-not (or staattinen-taulukko? dynaaminen-taulukko? rivi?)
       rajapinnankasittely
       (let [osamaaritelman-id (get-in osamaaritelma [:conf ::id])
             rajapinnan-nimi (muodosta-rajapinnan-nimi-polusta (get-in *osamaaritelmien-polut* [osamaaritelman-id :datapolku]))
             staattisen-gridin-uusi-datakasittelija (fn [rajapinnankasittely osan-maarittelman-nimi]
                                                      (muodosta-rajapintakasittelija-taulukko (get osamaaritelma osan-maarittelman-nimi)
                                                                                              vaihto-osat-mukaan?
                                                                                              rajapinnankasittely))
             stattisen-gridin-datakasittelija (fn [rajapinnankasittely]
                                                (reduce (fn [rajapinnankasittely seuraava-osamaaritelma]
                                                          (muodosta-rajapintakasittelija-taulukko seuraava-osamaaritelma
                                                                                                  vaihto-osat-mukaan?
                                                                                                  rajapinnankasittely))
                                                        rajapinnankasittely
                                                        (:body osamaaritelma)))
             uusi-rajapinnankasittely (if staattinen-taulukko?
                                        (cond-> rajapinnankasittely
                                                (:header osamaaritelma) (staattisen-gridin-uusi-datakasittelija :header)
                                                (:body osamaaritelma) stattisen-gridin-datakasittelija
                                                (:footer osamaaritelma) (staattisen-gridin-uusi-datakasittelija :footer))
                                        (assoc rajapinnankasittely
                                               (get-in *osamaaritelmien-polut* [osamaaritelman-id :gridpolku])
                                               (muodosta-rajapintakasittelija-taulukko-maaritelma osamaaritelma rajapinnan-nimi)))]
         (if-let [vaihdettava-osa (and vaihto-osat-mukaan?
                                       (get-in osamaaritelma [:conf :vaihdettava-osa]))]
           (let [vaihdettavan-osan-maaritelma (get-in @*vaihto-osien-mappaus* [:vaihto-osien-maaritelmat vaihdettava-osa])
                 taman-osan-polut (get *osamaaritelmien-polut* (get-in osamaaritelma [:conf ::id]))]
             (merge uusi-rajapinnankasittely
                    (binding [*osamaaritelmien-polut* (update *osamaaritelmien-polut*
                                                              (get-in vaihdettavan-osan-maaritelma [:conf ::id])
                                                              (fn [polut]
                                                                (merge-with (fn [taman-polku vaihdettavan-polku]
                                                                              (vec (concat taman-polku (rest vaihdettavan-polku))))
                                                                            taman-osan-polut
                                                                            polut)))]
                      (muodosta-rajapintakasittelija-taulukko vaihdettavan-osan-maaritelma
                                                              vaihto-osat-mukaan?
                                                              uusi-rajapinnankasittely))))
           uusi-rajapinnankasittely))))))

(defn muodosta-osamaaritelmien-polut
  ([taulukkomaaritelma vaihto-osamaaritelmat init-datapolku] (muodosta-osamaaritelmien-polut taulukkomaaritelma vaihto-osamaaritelmat nil {} [] init-datapolku [] nil))
  ([osamaaritelma vaihto-osamaaritelmat entinen-osamaaritelma kootut-polut edellinen-gridpolku edellinen-datapolku edellinen-osapolku taman-osan-index]
   (let [staattinen-taulukko? (staattinen-taulukko? osamaaritelma)
         dynaaminen-taulukko? (dynaaminen-taulukko? osamaaritelma)
         rivi? (rivi? osamaaritelma)
         root-osa? (nil? entinen-osamaaritelma)
         vaihdettava-osa (get-in osamaaritelma [:conf :vaihdettava-osa])
         osan-polut {:gridpolku (uusi-gridpolku edellinen-gridpolku root-osa? taman-osan-index (get-in osamaaritelma [:conf :nimi]))
                     :datapolku (uusi-datapolku edellinen-datapolku root-osa? taman-osan-index (get-in osamaaritelma [:conf :nimi]))
                     :osapolku (conj edellinen-osapolku
                                     (cond
                                       staattinen-taulukko? :staattinen-taulukko
                                       dynaaminen-taulukko? :dynaaminen-taulukko
                                       rivi? :rivi))}
         osamaaritelman-id (get-in osamaaritelma [:conf ::id])
         staattisen-gridin-uudet-polut (fn [kootut-polut osan-maarittelman-nimi header-annettu?]
                                         (muodosta-osamaaritelmien-polut (get osamaaritelma osan-maarittelman-nimi)
                                                                         vaihto-osamaaritelmat
                                                                         osamaaritelma
                                                                         kootut-polut
                                                                         (:gridpolku osan-polut)
                                                                         #_(if root-osa?
                                                                           (:gridpolku osan-polut)
                                                                           (vec (butlast (:gridpolku osan-polut))))
                                                                         (:datapolku osan-polut)
                                                                         #_(if root-osa?
                                                                           (:datapolku osan-polut)
                                                                           (vec (butlast (:datapolku osan-polut))))
                                                                         (:osapolku osan-polut)
                                                                         (if (= :header osan-maarittelman-nimi)
                                                                           0
                                                                           (let [body-osien-maara (count (:body osamaaritelma))]
                                                                             (if header-annettu?
                                                                               (inc body-osien-maara)
                                                                               body-osien-maara)))))
         stattisen-gridin-polut (fn [kootut-polut header-annettu?]
                                  (second (reduce (fn [[index kootut-polut] seuraava-osamaaritelma]
                                                    [(inc index)
                                                     (merge kootut-polut
                                                            (muodosta-osamaaritelmien-polut seuraava-osamaaritelma
                                                                                            vaihto-osamaaritelmat
                                                                                            osamaaritelma
                                                                                            kootut-polut
                                                                                            edellinen-gridpolku
                                                                                            #_(conj edellinen-gridpolku (if header-annettu?
                                                                                                                        (inc index)
                                                                                                                        index))
                                                                                            (:datapolku osan-polut)
                                                                                            #_(conj (:datapolku osan-polut) index)
                                                                                            (:osapolku osan-polut)
                                                                                            (if header-annettu?
                                                                                              (inc index)
                                                                                              index)))])
                                                  [0 kootut-polut]
                                                  (:body osamaaritelma))))
         kaikki-polut (cond
                        staattinen-taulukko? (cond-> (assoc kootut-polut osamaaritelman-id osan-polut)
                                                     (:header osamaaritelma) (staattisen-gridin-uudet-polut :header true)
                                                     (:body osamaaritelma) (stattisen-gridin-polut (boolean (:header osamaaritelma)))
                                                     (:footer osamaaritelma) (staattisen-gridin-uudet-polut :footer (boolean (:header osamaaritelma))))
                        dynaaminen-taulukko? (muodosta-osamaaritelmien-polut (:toistettava-osa osamaaritelma)
                                                                             vaihto-osamaaritelmat
                                                                             osamaaritelma
                                                                             (assoc kootut-polut osamaaritelman-id osan-polut)
                                                                             (:gridpolku osan-polut)
                                                                             (:datapolku osan-polut)
                                                                             (:osapolku osan-polut)
                                                                             0)
                        rivi? (muodosta-osamaaritelmien-polut (:osat osamaaritelma)
                                                              vaihto-osamaaritelmat
                                                              osamaaritelma
                                                              (assoc kootut-polut osamaaritelman-id osan-polut)
                                                              (:gridpolku osan-polut)
                                                              (:datapolku osan-polut)
                                                              (:osapolku osan-polut)
                                                              0)
                        :else kootut-polut)]
     (if vaihdettava-osa
       (muodosta-osamaaritelmien-polut (get vaihto-osamaaritelmat vaihdettava-osa)
                                       vaihto-osamaaritelmat
                                       osamaaritelma
                                       kaikki-polut
                                       edellinen-gridpolku
                                       edellinen-datapolku
                                       edellinen-osapolku
                                       taman-osan-index)
       kaikki-polut))))

(s/def ::nimi (s/or :keyword keyword?
                    :string string?))

(s/def ::data-polku vector?)
(s/def ::grid-polku vector?)
(s/def ::dom-id string?)
(s/def ::ratom #(implements? reagent.ratom/IReactiveAtom %))
(s/def ::tee-taulukko-conf (s/keys :req-un [::ratom ::dom-id ::grid-polku ::data-polku]))

(defn tee-taulukko!
  [maaritelma]
  {:pre [(s/conform ::tee-taulukko-conf (:conf maaritelma))]}
  (let [{:keys [conf datavaikutukset-taulukkoon taulukko]} maaritelma
        taytetty-taulukkomaaritelma (tayta-taulukkomaaritelma taulukko)
        taytetty-vaihto-osamaaritelma (tayta-taulukkomaaritelma (:vaihto-osat maaritelma))
        vaihto-osien-mappaus (atom {:vaihto-osat {}
                                    :vaihto-osien-maaritelmat taytetty-vaihto-osamaaritelma
                                    :mappaus {}})
        _ (binding [*vaihto-osien-mappaus* vaihto-osien-mappaus]
            (muodosta-vaihto-osat! vaihto-osien-mappaus taytetty-vaihto-osamaaritelma))
        osamaaritelmien-polut (muodosta-osamaaritelmien-polut taytetty-taulukkomaaritelma taytetty-vaihto-osamaaritelma (:data-polku conf))
        g (assoc (binding [*vaihto-osien-mappaus* vaihto-osien-mappaus]
                      (muodosta-grid conf taytetty-taulukkomaaritelma))
                    ::vaihto-osien-mappaus vaihto-osien-mappaus)
        datakasittely-ratom (binding [*osamaaritelmien-polut* osamaaritelmien-polut]
                              (muodosta-datakasittelija-ratom taytetty-taulukkomaaritelma))
        datakasittely-ratom-muokkaus {}
        datakasittely-ratom-trigger {}
        rajapinta (into {} (map (fn [k] [k any?]) (keys datakasittely-ratom)))
        rajapintakasittelija-taulukko (binding [*vaihto-osien-mappaus* vaihto-osien-mappaus
                                                *osamaaritelmien-polut* osamaaritelmien-polut]
                                        (muodosta-rajapintakasittelija-taulukko taytetty-taulukkomaaritelma))
        #_#_gridin-tapahtumat (muodosta-grid-tapahtumat)]
    (println "grid " g)
    (println "rajapinta " rajapinta)
    (println "datakasittely-ratom " datakasittely-ratom)
    (println "rajapintakasittelija-taulukko: " rajapintakasittelija-taulukko)
    (println "osamaaritelmien-polut: " osamaaritelmien-polut)
    (swap! (:ratom conf) assoc-in (:grid-polku conf) g)
    (grid/rajapinta-grid-yhdistaminen! g
                                       rajapinta
                                       (grid/datan-kasittelija (:ratom conf)
                                                               rajapinta
                                                               datakasittely-ratom
                                                               datakasittely-ratom-muokkaus
                                                               datakasittely-ratom-trigger)
                                       rajapintakasittelija-taulukko)
    #_(grid/grid-tapahtumat grid
                            ratom
                            gridin-tapahtumat)))
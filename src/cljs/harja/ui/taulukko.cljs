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

(defn polku-indeksista [polun-osa index]
  (if (= ::vektori polun-osa)
    index
    polun-osa))

(defn poista-tyhjat [arvo]
  (clj-str/replace arvo #"\s" ""))

(defn paivita-raidat! [g]
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
      (when rivi
        (grid/paivita-grid! rivi
                            :parametrit
                            (fn [parametrit]
                              (update parametrit :class (fn [luokat]
                                                          (if (::samaraita-edelliseen? rivi)
                                                            (paivita-luokat luokat (not (odd? index)))
                                                            (paivita-luokat luokat (odd? index)))))))
        (recur loput-rivit
               (if (::samaraita-edelliseen? rivi)
                 index
                 (inc index)))))))

(defn paivita-solun-arvo! [{:keys [paivitettava-asia arvo solu ajettavat-jarjestykset triggeroi-seuranta?]
                            :or {ajettavat-jarjestykset false triggeroi-seuranta? false}}]
  (jarjesta-data ajettavat-jarjestykset
    (triggeroi-seurannat triggeroi-seuranta?
      (grid/aseta-rajapinnan-data!
        (grid/osien-yhteinen-asia solu :datan-kasittelija)
        paivitettava-asia
        arvo
        (grid/solun-asia solu :tunniste-rajapinnan-dataan)))))

(defn- staattinen-taulukko? [osan-maaritelma]
  (not (empty? (select-keys osan-maaritelma #{:header :body :footer}))))

(defn- dynaaminen-taulukko? [osan-maaritelma]
  (get osan-maaritelma :toistettava-osa))

(defn- rivi? [osan-maaritelma]
  (contains? osan-maaritelma :osat))

(defn- solu-conf? [osan-maaritelma]
  (contains? osan-maaritelma :solu))

(defn tayta-alla-olevat-rivit! [asettajan-nimi rivit-alla arvo]
  (when (and arvo (not (empty? rivit-alla)))
    (let [sarake-index (first (keep-indexed (fn [index osa]
                                              (when (= (grid/hae-osa osa :id) (grid/hae-osa solu/*this* :id))
                                                index))
                                            (grid/hae-grid (grid/vanhempi solu/*this*) :lapset)))]
      (doseq [rivi rivit-alla
              :let [sarakkeen-solu (grid/get-in-grid rivi [sarake-index])]]
        (paivita-solun-arvo! {:paivitettava-asia asettajan-nimi
                              :arvo arvo
                              :solu sarakkeen-solu
                              :ajettavat-jarjestykset true
                              :triggeroi-seuranta? false})))))

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
                                                    :solu solu/*this*
                                                    :ajettavat-jarjestykset true})))
               :on-blur (fn [arvo]
                          (when arvo
                            (paivita-solun-arvo! {:paivitettava-asia :aseta-arvo!
                                                  :arvo arvo
                                                  :solu solu/*this*
                                                  :ajettavat-jarjestykset :deep
                                                  :triggeroi-seuranta? true})))
               :on-key-down (fn [event]
                              (when (= "Enter" (.. event -key))
                                (.. event -target blur)))}
   :parametrit {:size 2
                :class #{"input-default"}}})

(defmethod predef :syote-tayta-alas
  [_ conf]
  {:nappia-painettu! (fn [rivit-alla arvo]
                       (let [grid (grid/root solu/*this*)]
                         (tayta-alla-olevat-rivit! :aseta-arvo! rivit-alla arvo)
                         (paivita-solun-arvo! {:paivitettava-asia :aseta-arvo!
                                               :arvo arvo
                                               :solu solu/*this*
                                               :ajettavat-jarjestykset :deep
                                               :triggeroi-seuranta? true})
                         (grid/jarjesta-grid-data! grid)))
   :on-focus (fn [_]
               (grid/paivita-osa! solu/*this*
                                  (fn [solu]
                                    (assoc solu :nappi-nakyvilla? true))))})

(defn laajenna-solua-klikattu [laajennasolu aukeamispolku sulkemispolku auki?]
  (let [g (grid/root laajennasolu)]
    (if auki?
      (do (grid/nayta! (grid/osa-polusta laajennasolu aukeamispolku))
          (paivita-raidat! g))
      (do (grid/piillota! (grid/osa-polusta laajennasolu sulkemispolku))
          (paivita-raidat! g)))))

(defmethod predef :laajenna
  [_ {:keys [aukeamispolku sulkemispolku aukaise-fn]}]
  {:aukaise-fn (fn [this auki?]
                 (when aukaise-fn
                   (aukaise-fn this auki?))
                 (laajenna-solua-klikattu this aukeamispolku sulkemispolku auki?))
   :auki-alussa? false})

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

(defn recur-osamaaritelma
  ([recur-fn osamaaritelma kuljetettava-data] (recur-osamaaritelma recur-fn osamaaritelma kuljetettava-data nil))
  ([recur-fn osamaaritelma kuljetettava-data index]
   (let [staattinen-taulukko? (staattinen-taulukko? osamaaritelma)
         dynaaminen-taulukko? (dynaaminen-taulukko? osamaaritelma)
         rivi? (rivi? osamaaritelma)
         solu? (solu-conf? osamaaritelma)]
     (if-not (or staattinen-taulukko? dynaaminen-taulukko? rivi? solu?)
       kuljetettava-data
       (let [maaritelman-tyyppi (cond
                                  staattinen-taulukko? :staattinen-taulukko
                                  dynaaminen-taulukko? :dynaaminen-taulukko
                                  rivi? :rivi
                                  solu? :solu)
             staattisen-gridin-uusi-datakasittelija (fn [kuljetettava-data osan-maarittelman-nimi header-annettu?]
                                                      (recur-osamaaritelma recur-fn
                                                                           (get osamaaritelma osan-maarittelman-nimi)
                                                                           kuljetettava-data
                                                                           (if (= :header osan-maarittelman-nimi)
                                                                             0
                                                                             (let [body-osien-maara (count (:body osamaaritelma))]
                                                                               (if header-annettu?
                                                                                 (inc body-osien-maara)
                                                                                 body-osien-maara)))))
             stattisen-gridin-datakasittelija (fn [kuljetettava-data header-annettu?]
                                                (second (reduce (fn [[index kuljetettava-data] seuraava-osamaaritelma]
                                                                  [(inc index)
                                                                   (merge kuljetettava-data
                                                                          (recur-osamaaritelma recur-fn
                                                                                               seuraava-osamaaritelma
                                                                                               kuljetettava-data
                                                                                               (if header-annettu?
                                                                                                 (inc index)
                                                                                                 index)))])
                                                                [0 kuljetettava-data]
                                                                (:body osamaaritelma))))
             kuljetettava-data (recur-fn osamaaritelma kuljetettava-data maaritelman-tyyppi index)]
         (cond
           staattinen-taulukko? (cond-> kuljetettava-data
                                        (:header osamaaritelma) (staattisen-gridin-uusi-datakasittelija :header true)
                                        (:body osamaaritelma) (stattisen-gridin-datakasittelija (boolean (:header osamaaritelma)))
                                        (:footer osamaaritelma) (staattisen-gridin-uusi-datakasittelija :footer (boolean (:header osamaaritelma))))
           dynaaminen-taulukko? (recur-osamaaritelma recur-fn (get osamaaritelma :toistettava-osa) kuljetettava-data nil)
           rivi? (recur-osamaaritelma recur-fn (get osamaaritelma :osat) kuljetettava-data nil)
           solu? (recur-osamaaritelma recur-fn nil kuljetettava-data nil)))))))

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

(defn vaihda-osa-takaisin! [osa yksiloivadata vaihdettavan-osan-polku tila-atom root-datapolku]
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
        {:keys [vanha-osa vaihto-osan-tunniste vaihto-osan-koko]} (get-in @vaihto-osien-mappaus [:vaihdettu vaihdettavan-osan-id])]
    (swap! vaihto-osien-mappaus update :vaihdettu dissoc vaihdettavan-osan-id)
    (grid/vaihda-osa!
      vaihdettava-osa
      (constantly vanha-osa))
    (grid/aseta-grid! vanha-osa :koko vaihto-osan-koko)
    (swap! tila-atom update-in (conj root-datapolku ::vaihdetut-osat vaihto-osan-tunniste yksiloivadata) not)))

(declare muodosta-grid-osa!)

(defn vaihda-osa! [osa yksiloivadata vaihdettavan-osan-polku tila-atom root-datapolku]
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
        vaihto-osan-koko (grid/hae-grid vaihdettava-osa :koko)
        uusi-osa (binding [*vaihto-osien-mappaus* vaihto-osien-mappaus
                           *otsikon-nimi* (::seurattavan-otsikon-nimi osa)]
                   (muodosta-grid-osa! (get-in @vaihto-osien-mappaus [:vaihto-osien-maaritelmat vaihto-osan-tunniste])))]
    (grid/vaihda-osa!
      vaihdettava-osa
      (constantly uusi-osa))
    (swap! vaihto-osien-mappaus
           (fn [mappaukset]
             (update-in mappaukset
                        [:vaihdettu (grid/hae-osa (grid/get-in-grid root-grid vaihdettavan-osan-polku) :id)]
                        (fn [m]
                          (assoc m
                                 :vanha-osa vaihdettava-osa
                                 :vaihto-osan-tunniste vaihto-osan-tunniste
                                 :vaihto-osan-koko vaihto-osan-koko)))))
    (swap! tila-atom update-in (conj root-datapolku ::vaihdetut-osat vaihto-osan-tunniste yksiloivadata) not)))

(declare muodosta-grid-rivi muodosta-grid-staattinen-taulukko muodosta-grid-dynaaminen-taulukko)

(defn vaihdettavan-osan-ilmoitus! [osan-maaritelma osan-id]
  (when-let [vaihdettavan-osan-nimi (get-in osan-maaritelma [:conf :vaihdettava-osa])]
    (when g-debug/GRID_DEBUG
      (when (nil? *vaihto-osien-mappaus*)
        (warn "*vaihto-osien-mappaus* on nil vaikka siihen yritetään asettaa vaihdettava-osa!")))
    (swap! *vaihto-osien-mappaus*
           (fn [mappaukset]
             (update mappaukset :mappaus assoc osan-id vaihdettavan-osan-nimi)))))

(defn- muodosta-grid-osa!
  [osan-maaritelma]
  (let [muodostettu-osa (cond
                          (staattinen-taulukko? osan-maaritelma) (muodosta-grid-staattinen-taulukko osan-maaritelma)
                          (dynaaminen-taulukko? osan-maaritelma) (muodosta-grid-dynaaminen-taulukko osan-maaritelma)
                          (rivi? osan-maaritelma) (muodosta-grid-rivi osan-maaritelma)
                          (solu-conf? osan-maaritelma) (apply (:solu osan-maaritelma) (or (:parametrit osan-maaritelma) [])))
        muodostettu-osa (if (get-in osan-maaritelma [:conf :samaraita-edelliseen?])
                          (assoc muodostettu-osa ::samaraita-edelliseen? true)
                          muodostettu-osa)
        muodostettu-osa (assoc muodostettu-osa ::seurattavan-otsikon-nimi *otsikon-nimi*)
        muodostettu-osa (walk/prewalk (fn [x]
                                        (if (and (map-entry? x)
                                                 (fn? (second x))
                                                 (-> x second meta :aja-taulukon-luontivaiheessa?))
                                          (update x 1 (fn [f] (f)))
                                          x))
                                      muodostettu-osa)]
    (vaihdettavan-osan-ilmoitus! osan-maaritelma (grid/hae-osa muodostettu-osa :id))
    muodostettu-osa))

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
  (let [{:keys [nimi luokat yksiloivakentta]} (:conf taulukkomaaritelma)
        vaihto-osien-mappaus *vaihto-osien-mappaus*
        otsikon-nimi *otsikon-nimi*]
    (grid/dynamic-grid {:nimi (or nimi ::data)
                        :alueet [{:sarakkeet [0 1] :rivit [0 1]}]
                        :osatunnisteet (fn [data]
                                         (map #(get % yksiloivakentta) data))
                        :koko konf/auto
                        :luokat (clj-set/union #{"salli-ylipiirtaminen"} luokat)
                        :osien-maara-muuttui! (fn [g _]
                                                (paivita-raidat! g))
                        :toistettavan-osan-data identity
                        :toistettava-osa (fn [rivien-data]
                                           (mapv (fn [rivin-data]
                                                   (binding [*vaihto-osien-mappaus* vaihto-osien-mappaus
                                                             *otsikon-nimi* otsikon-nimi]
                                                     (muodosta-grid-osa! (:toistettava-osa taulukkomaaritelma))))
                                                 rivien-data))})))

(defn- muodosta-grid-staattinen-taulukko
  ([taulukkomaaritelma] (muodosta-grid-staattinen-taulukko taulukkomaaritelma nil))
  ([taulukkomaaritelma root-conf]
   (let [{:keys [rivi-n nimet-osioihin header-index body-index footer-index]} (taulukon-koko-conf taulukkomaaritelma)
         {:keys [nimi koko luokat]} (get taulukkomaaritelma :conf)
         header-osa (when header-index
                      (muodosta-grid-osa! (:header taulukkomaaritelma)))
         headerosa-koko-maarityksella (first
                                        (grid/hae-kaikki-osat header-osa
                                                              (every-pred grid/rivi?
                                                                          #(boolean (grid/hae-osa % :nimi))
                                                                          #(nil? (get (grid/hae-grid % :koko) :seurattava)))))]
     (binding [*otsikon-nimi* (if (and headerosa-koko-maarityksella
                                       (-> taulukkomaaritelma (get-in [:conf :koko]) meta :yksittainen not))
                                (grid/hae-osa headerosa-koko-maarityksella :nimi)
                                *otsikon-nimi*)]
       (grid/grid (merge {:nimi nimi
                          :alueet [{:sarakkeet [0 1] :rivit [0 rivi-n]}]
                          :luokat (clj-set/union #{"salli-ylipiirtaminen"} luokat)
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

(defn uusi-datapolku
  [entinen-polku root-osa? taman-osan-index taman-osan-nimi nimi-generoitu? dynaaminen-taulukko?]
  (cond
    root-osa? entinen-polku
    (and nimi-generoitu? dynaaminen-taulukko?) (conj entinen-polku ::vektori)
    taman-osan-nimi (conj entinen-polku taman-osan-nimi)))

(defn uusi-gridpolku [entinen-polku root-osa? taman-osan-index taman-osan-nimi]
  (cond
    root-osa? entinen-polku
    taman-osan-nimi (conj entinen-polku taman-osan-nimi)
    :else (conj entinen-polku taman-osan-index)))

(defn muodosta-rajapinnan-nimi-polusta
  [polku]
  (fn nimi-fn [& indeksit]
    (apply str (interpose "-"
                          (reduce (fn [polku indeksi]
                                    (let [polku (mapv (fn [polun-osa]
                                                        (if (and (keyword polun-osa)
                                                                 (not= polun-osa ::vektori))
                                                          (name polun-osa)
                                                          polun-osa))
                                                      polku)
                                          polku-dynaamiseen-osaan (vec (take-while #(not= ::vektori %) polku))
                                          polku-dynaamisesta-osasta (vec (drop-while #(not= ::vektori %) polku))]
                                      (vec (concat (cond
                                                     indeksi (conj polku-dynaamiseen-osaan indeksi)
                                                     (= polku-dynaamiseen-osaan polku) polku
                                                     :else (conj polku-dynaamiseen-osaan "_vektori_"))
                                                   (rest polku-dynaamisesta-osasta)))))
                                  polku
                                  indeksit)))))

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
          (fn [osamaaritelma _]
            (let [osamaaritelman-id (get-in osamaaritelma [:conf ::id])
                  osapolku (get-in *osamaaritelmien-polut* [osamaaritelman-id :osapolku])
                  dynaamisen-sisalla? (some #(= % :dynaaminen-taulukko) (butlast osapolku))
                  dynaaminen-taulukko? (dynaaminen-taulukko? osamaaritelma)]
              (cond
                (and dynaaminen-taulukko? dynaamisen-sisalla?) [:dynaaminen-taulukko :dynaamisen-sisalla]
                dynaamisen-sisalla? :dynaamisen-sisalla
                dynaaminen-taulukko? :dynaaminen-taulukko
                (staattinen-taulukko? osamaaritelma) :staattinen-taulukko
                (rivi? osamaaritelma) :rivi
                :else :default))))

(defmethod muodosta-datakasittelija-ratom-maaritelma :staattinen-taulukko
  [osamaaritelma {:keys [polku-dataan]}]
  {:polut [polku-dataan]
   :haku identity})

(defmethod muodosta-datakasittelija-ratom-maaritelma :dynaaminen-taulukko
  [taulukkomaaritelma {:keys [root-datapolku polku-dataan]}]
  (let [{:keys [yksiloivakentta]
         generoi-yksiloivakentta? ::generoi-yksiloivakentta?} taulukkomaaritelma
        taulukon-vaihdettavat-osat (atom [])
        _ (walk/postwalk (fn [x]
                           (when-let [vaihdettava-osa (and (map? x)
                                                           (get-in x [:conf :vaihdettava-osa]))]
                             (swap! taulukon-vaihdettavat-osat
                                    conj
                                    [vaihdettava-osa
                                     (muodosta-rajapinnan-nimi-polusta (get-in *osamaaritelmien-polut* [(get-in x [:conf ::id]) :datapolku]))]))
                           x)
                         taulukkomaaritelma)
        vaihdettavien-osien-polut (mapv (fn [[vaihdettava-osa _]]
                                          (conj root-datapolku ::vaihdetut-osat vaihdettava-osa))
                                        @taulukon-vaihdettavat-osat)
        taulukon-vaihdettavat-osat-arvot @taulukon-vaihdettavat-osat]
    {:polut (vec (cons polku-dataan vaihdettavien-osien-polut))
     :luonti-init (fn [tila & _]
                    (if generoi-yksiloivakentta?
                      (update-in tila polku-dataan yksiloivan-datan-generointi yksiloivakentta)
                      tila))
     :haku (fn [data & vaihdettavat-osat]
             (let [vaihdettavat-osat (into {}
                                           (map (fn [kaytossa-osat [vaihdettava-osa rajapinnan-nimi-fn]]
                                                  [vaihdettava-osa {:kaytossa-osat kaytossa-osat :rajapinnan-nimi-fn rajapinnan-nimi-fn}])
                                                vaihdettavat-osat
                                                taulukon-vaihdettavat-osat-arvot))]
               {:harja.ui.taulukko.impl.grid/jarjestettava-data data
                :harja.ui.taulukko.impl.grid/muu-data vaihdettavat-osat}))
     :identiteetti {1 #(get % yksiloivakentta)}}))

(defmethod muodosta-datakasittelija-ratom-maaritelma :rivi
  [osamaaritelma {:keys [polku-dataan]}]
  {:polut [polku-dataan]
   :haku identity})

(defn dynaamisen-datan-polut [datapolku data]
  (second (reduce (fn [[index polut] datapoint]
                    (let [polku-dynaamiseen-osaan (vec (take-while #(not= ::vektori %) datapolku))
                          polku-dynaamisesta-osasta (vec (drop-while #(not= ::vektori %) datapolku))
                          polku-seuraavaan-dynaamiseen-osaan (vec (take-while #(not= ::vektori %) (rest polku-dynaamisesta-osasta)))
                          seuraava-dynaaminen-osa-olemassa? (not= polku-seuraavaan-dynaamiseen-osaan (rest polku-dynaamisesta-osasta))
                          polku-indexiin (conj polku-dynaamiseen-osaan index)]
                      [(inc index)
                       (if seuraava-dynaaminen-osa-olemassa?
                         (vec (concat polut
                                      (mapv (fn [polku]
                                              (vec (concat polku-indexiin
                                                           polku)))
                                            (dynaamisen-datan-polut (vec (rest polku-dynaamisesta-osasta))
                                                                    (get-in datapoint polku-seuraavaan-dynaamiseen-osaan)))))
                         (conj polut (vec (concat polku-indexiin
                                                  (rest polku-dynaamisesta-osasta)))))]))
                  [0 []]
                  data)))

(defmethod muodosta-datakasittelija-ratom-maaritelma :dynaamisen-sisalla
  [osamaaritelma {:keys [yksiloivakentta polku-dataan root-datapolku]}]
  (let [osamaaritelman-id (get-in osamaaritelma [:conf ::id])
        haettavan-datan-polku (get-in *osamaaritelmien-polut* [osamaaritelman-id :datapolku])
        osapolku (get-in *osamaaritelmien-polut* [osamaaritelman-id :osapolku])
        polku-ensimmaiseen-dynaamiseen-osaan (vec (take (+ (count (take-while #(not= :dynaaminen-taulukko %)
                                                                              osapolku))
                                                           (count root-datapolku))
                                                        haettavan-datan-polku))]
    {:polut [polku-ensimmaiseen-dynaamiseen-osaan]
     :luonti (fn [data]
               (mapv (fn [polku]
                       (let [rajapinnan-nimi-fn (muodosta-rajapinnan-nimi-polusta polku)]
                         {(rajapinnan-nimi-fn nil) [polku]}))
                     (dynaamisen-datan-polut (if-not (= polku-dataan haettavan-datan-polku)
                                               polku-dataan
                                               haettavan-datan-polku)
                                             data)))
     :haku identity}))

(defn split-vektori [polku]
  (loop [[polun-osa & loput] polku
         alku []]
    (if (= ::vektori polun-osa)
      [alku (vec loput)]
      (recur loput
             (conj alku polun-osa)))))

(defmethod muodosta-datakasittelija-ratom-maaritelma [:dynaaminen-taulukko :dynaamisen-sisalla]
  [taulukkomaaritelma {:keys [root-datapolku polku-dataan]}]
  (let [{:keys [yksiloivakentta]
         generoi-yksiloivakentta? ::generoi-yksiloivakentta?} taulukkomaaritelma
        taulukon-vaihdettavat-osat (atom [])
        _ (walk/postwalk (fn [x]
                           (when-let [vaihdettava-osa (and (map? x)
                                                           (get-in x [:conf :vaihdettava-osa]))]
                             (swap! taulukon-vaihdettavat-osat
                                    conj
                                    [vaihdettava-osa
                                     (muodosta-rajapinnan-nimi-polusta (get-in *osamaaritelmien-polut* [(get-in x [:conf ::id]) :datapolku]))]))
                           x)
                         taulukkomaaritelma)
        vaihdettavien-osien-polut (mapv (fn [[vaihdettava-osa _]]
                                          (conj root-datapolku ::vaihdetut-osat vaihdettava-osa))
                                        @taulukon-vaihdettavat-osat)
        taulukon-vaihdettavat-osat-arvot @taulukon-vaihdettavat-osat
        osamaaritelman-id (get-in taulukkomaaritelma [:conf ::id])
        haettavan-datan-polku (get-in *osamaaritelmien-polut* [osamaaritelman-id :datapolku])
        osapolku (get-in *osamaaritelmien-polut* [osamaaritelman-id :osapolku])
        polku-ensimmaiseen-dynaamiseen-osaan (vec (take (+ (count (take-while #(not= :dynaaminen-taulukko %)
                                                                              osapolku))
                                                           (count root-datapolku))
                                                        haettavan-datan-polku))]
    {:polut (vec (cons polku-ensimmaiseen-dynaamiseen-osaan vaihdettavien-osien-polut))
     :luonti (fn [& args]
               (let [data (first args)]
                 (mapv (fn [polku]
                         (let [rajapinnan-nimi-fn (muodosta-rajapinnan-nimi-polusta polku)]
                           {(rajapinnan-nimi-fn nil) [polku]}))
                       (dynaamisen-datan-polut (if-not (= polku-dataan haettavan-datan-polku)
                                                 polku-dataan
                                                 haettavan-datan-polku)
                                               data))))
     :haku (fn [data & vaihdettavat-osat]
             (let [vaihdettavat-osat (into {}
                                           (map (fn [kaytossa-osat [vaihdettava-osa rajapinnan-nimi-fn]]
                                                  [vaihdettava-osa {:kaytossa-osat kaytossa-osat :rajapinnan-nimi-fn rajapinnan-nimi-fn}])
                                                vaihdettavat-osat
                                                taulukon-vaihdettavat-osat-arvot))]
               {:harja.ui.taulukko.impl.grid/jarjestettava-data data
                :harja.ui.taulukko.impl.grid/muu-data vaihdettavat-osat}))
     :identiteetti {1 #(get % yksiloivakentta)}}))

(defmethod muodosta-datakasittelija-ratom-maaritelma :default
  [osamaaritelma]
  (log/debug "foo"))

(defn muodosta-datakasittelija-ratom
  ([osamaaritelma root-datapolku] (muodosta-datakasittelija-ratom osamaaritelma root-datapolku {} {}))
  ([osamaaritelma root-datapolku datakasittely {:keys [yksiloivakentta]}]
   (let [staattinen-taulukko? (staattinen-taulukko? osamaaritelma)
         dynaaminen-taulukko? (dynaaminen-taulukko? osamaaritelma)
         rivi? (rivi? osamaaritelma)]
     (if-not (or staattinen-taulukko? dynaaminen-taulukko? rivi?)
       datakasittely
       (let [staattisen-gridin-uusi-datakasittelija (fn [datakasittely osan-maarittelman-nimi]
                                                      (merge datakasittely
                                                             (muodosta-datakasittelija-ratom (get osamaaritelma osan-maarittelman-nimi)
                                                                                             root-datapolku
                                                                                             datakasittely
                                                                                             {})))
             stattisen-gridin-datakasittelija (fn [datakasittely]
                                                (second (reduce (fn [[index datakasittely] seuraava-osamaaritelma]
                                                                  [(inc index)
                                                                   (merge datakasittely
                                                                          (muodosta-datakasittelija-ratom seuraava-osamaaritelma
                                                                                                          root-datapolku
                                                                                                          datakasittely
                                                                                                          {}))])
                                                                [0 datakasittely]
                                                                (:body osamaaritelma))))
             uusi-datakasittely (assoc datakasittely
                                       ((muodosta-rajapinnan-nimi-polusta (get-in *osamaaritelmien-polut* [(get-in osamaaritelma [:conf ::id]) :datapolku])) nil)
                                       (muodosta-datakasittelija-ratom-maaritelma osamaaritelma {:yksiloivakentta yksiloivakentta
                                                                                                 :polku-dataan (if-let [toinen-osamaaritelma (get-in *osamaaritelmien-polut* [(get-in osamaaritelma [:conf ::id]) :toisen-datapolku])]
                                                                                                                 (first (keep (fn [[_ {:keys [maaritelman-nimi datapolku]}]]
                                                                                                                                (when (= maaritelman-nimi toinen-osamaaritelma)
                                                                                                                                  datapolku))
                                                                                                                              *osamaaritelmien-polut*))
                                                                                                                 (get-in *osamaaritelmien-polut* [(get-in osamaaritelma [:conf ::id]) :datapolku]))
                                                                                                 :root-datapolku root-datapolku}))]
         (cond
           staattinen-taulukko? (cond-> uusi-datakasittely
                                        (:header osamaaritelma) (staattisen-gridin-uusi-datakasittelija :header)
                                        (:body osamaaritelma) stattisen-gridin-datakasittelija
                                        (:footer osamaaritelma) (staattisen-gridin-uusi-datakasittelija :footer))
           dynaaminen-taulukko? (muodosta-datakasittelija-ratom (get osamaaritelma :toistettava-osa) root-datapolku uusi-datakasittely {:yksiloivakentta (get-in osamaaritelma [:conf :yksiloivakentta])})
           rivi? (muodosta-datakasittelija-ratom (get osamaaritelma :osat) root-datapolku uusi-datakasittely {})))))))

(defn tayta-taulukkomaaritelma [taulukkomaaritelma]
  (letfn [(lisaa-nimi
            [conf nimi]
            (assoc conf :nimi nimi ::nimi-generoitu? true))
          (tarkista-staattisen-taulukon-nimet
            [x]
            (let [staattinen-taulukko? (and (map? x)
                                            (staattinen-taulukko? x))]
              (if staattinen-taulukko?
                (let [header-puuttuu-nimi? (and (:header x) (nil? (get-in x [:header :conf :nimi])))
                      footer-puuttuu-nimi? (and (:footer x) (nil? (get-in x [:footer :conf :nimi])))]
                  (cond-> x
                          header-puuttuu-nimi? (update-in [:header :conf] lisaa-nimi :header)
                          footer-puuttuu-nimi? (update-in [:footer :conf] lisaa-nimi :footer)))
                x)))
          (tarkista-osan-nimi
            [x]
            (if (and (map? x)
                     (nil? (get-in x [:conf :nimi])))
              (cond
                (staattinen-taulukko? x) (update x :conf lisaa-nimi :staattinen)
                (dynaaminen-taulukko? x) (update x :conf lisaa-nimi :dynaaminen)
                (rivi? x) (update x :conf lisaa-nimi :rivi)
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
  (let [taman-gridpolku (get-in *osamaaritelmien-polut* [(get-in osamaaritelma [:conf ::id]) :gridpolku])
        taman-gridpolun-pituus (count taman-gridpolku)]
    (reduce-kv (fn [m k v]
                 (assoc m
                        (fn [index]
                          (vec (concat [:. index] k)))
                        v))
               {}
               (binding [*osamaaritelmien-polut* (into {}
                                                       (map (fn [[osan-id polut]]
                                                              [osan-id (if (= (take taman-gridpolun-pituus (:gridpolku polut))
                                                                              taman-gridpolku)
                                                                         (update polut :gridpolku
                                                                                 (fn [gridpolku]
                                                                                   (vec (drop taman-gridpolun-pituus gridpolku))))
                                                                         polut)])
                                                            *osamaaritelmien-polut*))]
                 (muodosta-rajapintakasittelija-taulukko osamaaritelma true true)))))

(defn- muodosta-rajapintakasittelija-dynaaminen-osa [osamaaritelma]
  (let [rajapintakasittelijat (dynaamisen-osan-rajapintakasittelijat osamaaritelma)]
    (fn [g aikaisemmat-indeksit index]
      (let [indeksit (conj aikaisemmat-indeksit index)]
        (reduce-kv (fn [m k v]
                     (let [polku (k index)
                           maaritelma (-> v
                                          (update :rajapinta (fn [rajapinta-fn]
                                                               (apply rajapinta-fn indeksit)))
                                          (update :tunnisteen-kasittely (fn [tunnisteen-kasittely-fn]
                                                                          (tunnisteen-kasittely-fn indeksit))))
                           dynaaminen-taulukko? (boolean (:luonti maaritelma))
                           maaritelma (if dynaaminen-taulukko?
                                        (update maaritelma :luonti (fn [f] (partial f index)))
                                        maaritelma)]
                       (assoc m polku maaritelma)))
                   {}
                   rajapintakasittelijat)))))

(defn tunnisteen-kasittely [datapolku]
  (fn [_ data]
    (mapv (fn [[k _]]
            (conj datapolku k))
          data)))

(defmulti muodosta-rajapintakasittelija-taulukko-maaritelma
          (fn [osamaaritelma _ _ _]
            (cond
              (dynaaminen-taulukko? osamaaritelma) :dynaaminen-taulukko
              (rivi? osamaaritelma) :rivi
              :else :default)))

(defmethod muodosta-rajapintakasittelija-taulukko-maaritelma :dynaaminen-taulukko
  [taulukkomaaritelma rajapinnan-nimi datapolku dynaamisen-sisalla?]
  (let [jarjestys (get-in taulukkomaaritelma [:conf :jarjestys])
        yksiloivakentta (get-in taulukkomaaritelma [:conf :yksiloivakentta])]
    {:rajapinta rajapinnan-nimi
     :solun-polun-pituus 0
     :jarjestys jarjestys
     :datan-kasittely identity
     :tunnisteen-kasittely (if dynaamisen-sisalla?
                             (fn [indeksit]
                               (let [datapolku (loop [[polun-osa & loput-polusta] datapolku
                                                      luotu-polku []
                                                      indeksit indeksit]
                                                 (if polun-osa
                                                   luotu-polku
                                                   (recur loput-polusta
                                                          (conj luotu-polku
                                                                (if (= ::vektori polun-osa)
                                                                  (first indeksit)
                                                                  polun-osa))
                                                          (rest indeksit))))]
                                 (tunnisteen-kasittely datapolku)))
                             (tunnisteen-kasittely datapolku))
     :luonti (let [dynaamisen-sisus (muodosta-rajapintakasittelija-dynaaminen-osa (:toistettava-osa taulukkomaaritelma))]
               (fn [& args]
                 (let [aikaisemmat-indeksit (-> args butlast butlast vec)
                       [data vaihdettavien-osien-data] (-> args butlast last)
                       g (last args)]
                   (when data
                     (let [vaihto-osien-maaritelmat (-> g grid/root ::vaihto-osien-mappaus deref (get :vaihto-osien-maaritelmat))]
                       (map-indexed (fn [index datapoint]
                                      (let [datan-rajapinnat (dynaamisen-sisus g aikaisemmat-indeksit index)]
                                        (reduce-kv (fn [m polku maaritelma]
                                                     (let [maaritelma-kaytossa? (or (empty? vaihto-osien-maaritelmat)
                                                                                    (first (keep (fn [[vaihto-osan-nimi {:keys [kaytossa-osat rajapinnan-nimi-fn]}]]
                                                                                                   (let [kaytossa? (get kaytossa-osat (get datapoint yksiloivakentta))]
                                                                                                     (cond
                                                                                                       (= (rajapinnan-nimi-fn index) (:rajapinta maaritelma)) (not (true? kaytossa?))
                                                                                                       (try (re-find (re-pattern (str index "-" (name (get-in vaihto-osien-maaritelmat [vaihto-osan-nimi :conf :nimi]))))
                                                                                                                     (:rajapinta maaritelma))
                                                                                                            (catch :default _ false)) (true? kaytossa?)
                                                                                                       :else true)))
                                                                                                 vaihdettavien-osien-data)))]
                                                       (if maaritelma-kaytossa?
                                                         (assoc m polku maaritelma)
                                                         m)))
                                                   {}
                                                   datan-rajapinnat)))
                                    data))))))}))

(defmethod muodosta-rajapintakasittelija-taulukko-maaritelma :rivi
  [osamaaritelma rajapinnan-nimi datapolku dynaamisen-sisalla?]
  (let [jarjestys (get-in osamaaritelma [:conf :jarjestys])]
    {:rajapinta rajapinnan-nimi
     :solun-polun-pituus 1
     :jarjestys jarjestys
     :tunnisteen-kasittely (if dynaamisen-sisalla?
                             (fn [index]
                               (let [datapolku (mapv (fn [polun-osa]
                                                       (if (= ::vektori polun-osa)
                                                         index
                                                         polun-osa))
                                                     datapolku)]
                                 (tunnisteen-kasittely datapolku)))
                             (tunnisteen-kasittely datapolku))
     :datan-kasittely (fn [rivin-data]
                        (vec (vals rivin-data)))}))

(defmethod muodosta-rajapintakasittelija-taulukko-maaritelma :default
  [osamaaritelma rajapinnan-nimi]
  (log/debug "foo"))

(defn muodosta-rajapintakasittelija-taulukko
  ([osamaaritelma] (muodosta-rajapintakasittelija-taulukko osamaaritelma false false {}))
  ([osamaaritelma vaihto-osat-mukaan? dynaamisen-sisalla?] (muodosta-rajapintakasittelija-taulukko osamaaritelma vaihto-osat-mukaan? dynaamisen-sisalla? {}))
  ([osamaaritelma vaihto-osat-mukaan? dynaamisen-sisalla? rajapinnankasittely]
   (let [staattinen-taulukko? (staattinen-taulukko? osamaaritelma)
         dynaaminen-taulukko? (dynaaminen-taulukko? osamaaritelma)
         rivi? (rivi? osamaaritelma)]
     (if-not (or staattinen-taulukko? dynaaminen-taulukko? rivi?)
       rajapinnankasittely
       (let [osamaaritelman-id (get-in osamaaritelma [:conf ::id])
             rajapinnan-nimi-fn (muodosta-rajapinnan-nimi-polusta (get-in *osamaaritelmien-polut* [osamaaritelman-id :datapolku]))
             rajapinnan-nimi (if dynaamisen-sisalla?
                               rajapinnan-nimi-fn
                               (rajapinnan-nimi-fn nil))
             staattisen-gridin-uusi-datakasittelija (fn [rajapinnankasittely osan-maarittelman-nimi]
                                                      (muodosta-rajapintakasittelija-taulukko (get osamaaritelma osan-maarittelman-nimi)
                                                                                              vaihto-osat-mukaan?
                                                                                              dynaamisen-sisalla?
                                                                                              rajapinnankasittely))
             stattisen-gridin-datakasittelija (fn [rajapinnankasittely]
                                                (reduce (fn [rajapinnankasittely seuraava-osamaaritelma]
                                                          (muodosta-rajapintakasittelija-taulukko seuraava-osamaaritelma
                                                                                                  vaihto-osat-mukaan?
                                                                                                  dynaamisen-sisalla?
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
                                               (muodosta-rajapintakasittelija-taulukko-maaritelma osamaaritelma rajapinnan-nimi (get-in *osamaaritelmien-polut* [osamaaritelman-id :datapolku]) dynaamisen-sisalla?)))]
         (if-let [vaihdettava-osa (and vaihto-osat-mukaan?
                                       (get-in osamaaritelma [:conf :vaihdettava-osa]))]
           (let [vaihdettavan-osan-maaritelma (get-in @*vaihto-osien-mappaus* [:vaihto-osien-maaritelmat vaihdettava-osa])
                 taman-osan-polut (get *osamaaritelmien-polut* (get-in osamaaritelma [:conf ::id]))]
             (merge uusi-rajapinnankasittely
                    (binding [*osamaaritelmien-polut* (update *osamaaritelmien-polut*
                                                              (get-in vaihdettavan-osan-maaritelma [:conf ::id])
                                                              (fn [polut]
                                                                (merge-with (fn [taman-polku vaihdettavan-polku]
                                                                              (let [polku? (vector? taman-polku)]
                                                                                (if polku?
                                                                                  (vec (concat taman-polku (rest vaihdettavan-polku)))
                                                                                  vaihdettavan-polku)))
                                                                            taman-osan-polut
                                                                            polut)))]
                      (muodosta-rajapintakasittelija-taulukko vaihdettavan-osan-maaritelma
                                                              vaihto-osat-mukaan?
                                                              dynaamisen-sisalla?
                                                              uusi-rajapinnankasittely))))
           uusi-rajapinnankasittely))))))

(defn muodosta-trigger [{{:keys [kasittely-fn] riippuu-polut :polut} :riippuu-toisesta
                         {:keys [nimi]} :conf}
                        entinen-osamaaritelma
                        root-datapolku]
  (let [datapolku (get-in *osamaaritelmien-polut* [(get-in entinen-osamaaritelma [:conf ::id]) :datapolku])
        osan-datapolku (conj datapolku nimi)
        dynaamisen-sisalla? (some #(= ::vektori %) datapolku)
        trigger-nimi (str ((muodosta-rajapinnan-nimi-polusta osan-datapolku)
                           0)
                          "-seuranta")
        riippuu-datapolut (mapv (fn [polku]
                                  (reduce (fn [muodostettu-polku polun-osa]
                                            (case polun-osa
                                              :.. (vec (butlast muodostettu-polku))
                                              (conj muodostettu-polku polun-osa)))
                                          (case (first polku)
                                            :/ root-datapolku
                                            :.. (vec (butlast osan-datapolku)))
                                          (rest polku)))
                                riippuu-polut)
        luonti-polut (mapv (fn [polku]
                             (fn [index]
                               (mapv #(polku-indeksista % index)
                                     polku)))
                           riippuu-datapolut)
        polut (mapv (fn [datapolku]
                      (vec (take-while #(not= ::vektori %) datapolku)))
                    riippuu-datapolut)]
    (if dynaamisen-sisalla?
      {trigger-nimi {:polut polut
                     :luonti (fn [data]
                               (vec
                                 (map-indexed (fn [index _]
                                                {(keyword (str trigger-nimi "-" index)) (with-meta (mapv #(% index) luonti-polut)
                                                                                                   {:args [index]})})
                                              data)))
                     :aseta (fn [tila & args]
                              (let [index (last args)
                                    data-args (butlast args)
                                    osan-datapolku (mapv #(polku-indeksista % index) osan-datapolku)]
                                (assoc-in tila osan-datapolku (apply kasittely-fn data-args))))}}
      {trigger-nimi {:polut polut
                     :aseta (fn [tila & data-args]
                              (assoc-in tila osan-datapolku (apply kasittely-fn data-args)))}})))

(defn muodosta-triggerit
  ([osamaaritelma root-datapolku] (muodosta-triggerit osamaaritelma root-datapolku nil {}))
  ([osamaaritelma root-datapolku entinen-osamaaritelma triggerit]
   (let [staattinen-taulukko? (staattinen-taulukko? osamaaritelma)
         dynaaminen-taulukko? (dynaaminen-taulukko? osamaaritelma)
         rivi? (rivi? osamaaritelma)
         solu? (solu-conf? osamaaritelma)

         staattisen-gridin-paiden-triggerit (fn [triggerit osan-maarittelman-nimi]
                                              (muodosta-triggerit (get osamaaritelma osan-maarittelman-nimi)
                                                                  root-datapolku
                                                                  osamaaritelma
                                                                  triggerit))
         stattisen-gridin-bodyn-triggerit (fn [triggerit]
                                            (reduce (fn [triggerit seuraava-osamaaritelma]
                                                      (merge triggerit
                                                             (muodosta-triggerit seuraava-osamaaritelma
                                                                                 root-datapolku
                                                                                 osamaaritelma
                                                                                 triggerit)))
                                                    triggerit
                                                    (:body osamaaritelma)))]
     (cond
       staattinen-taulukko? (cond-> triggerit
                                    (:header osamaaritelma) (staattisen-gridin-paiden-triggerit :header)
                                    (:body osamaaritelma) (stattisen-gridin-bodyn-triggerit)
                                    (:footer osamaaritelma) (staattisen-gridin-paiden-triggerit :footer))
       dynaaminen-taulukko? (muodosta-triggerit (:toistettava-osa osamaaritelma)
                                                root-datapolku
                                                osamaaritelma
                                                triggerit)
       rivi? (reduce (fn [triggerit osa]
                       (muodosta-triggerit osa
                                           root-datapolku
                                           osamaaritelma
                                           triggerit))
                     triggerit
                     (:osat osamaaritelma))
       (and solu? (get osamaaritelma :riippuu-toisesta)) (merge triggerit
                                                                (muodosta-trigger osamaaritelma entinen-osamaaritelma root-datapolku))
       solu? triggerit))))

(defn muodosta-osamaaritelmien-polut
  ([taulukkomaaritelma vaihto-osamaaritelmat init-datapolku]
   (let [luodut-polut (muodosta-osamaaritelmien-polut taulukkomaaritelma vaihto-osamaaritelmat nil {} [] init-datapolku [] nil)]
     luodut-polut))
  ([osamaaritelma vaihto-osamaaritelmat entinen-osamaaritelma kootut-polut edellinen-gridpolku edellinen-datapolku edellinen-osapolku taman-osan-index]
   (let [entinen-osa-dynaaminen? (dynaaminen-taulukko? entinen-osamaaritelma)
         staattinen-taulukko? (staattinen-taulukko? osamaaritelma)
         dynaaminen-taulukko? (dynaaminen-taulukko? osamaaritelma)
         rivi? (rivi? osamaaritelma)
         root-osa? (nil? entinen-osamaaritelma)
         vaihdettava-osa (get-in osamaaritelma [:conf :vaihdettava-osa])
         osan-polut {:gridpolku (uusi-gridpolku edellinen-gridpolku root-osa? taman-osan-index (get-in osamaaritelma [:conf :nimi]))
                     :datapolku (uusi-datapolku edellinen-datapolku root-osa? taman-osan-index (get-in osamaaritelma [:conf :nimi]) (get-in osamaaritelma [:conf ::nimi-generoitu?]) entinen-osa-dynaaminen?)
                     :toisen-datapolku (when-let [toisen-osamaaritelman-nimi (get-in osamaaritelma [:conf :datapolku-maaritelmasta])]
                                         toisen-osamaaritelman-nimi)
                     :osapolku (conj edellinen-osapolku
                                     (cond
                                       staattinen-taulukko? :staattinen-taulukko
                                       dynaaminen-taulukko? :dynaaminen-taulukko
                                       rivi? :rivi))
                     :maaritelman-nimi (get-in osamaaritelma [:conf :nimi])}
         osamaaritelman-id (get-in osamaaritelma [:conf ::id])
         staattisen-gridin-uudet-polut (fn [kootut-polut osan-maarittelman-nimi header-annettu?]
                                         (muodosta-osamaaritelmien-polut (get osamaaritelma osan-maarittelman-nimi)
                                                                         vaihto-osamaaritelmat
                                                                         osamaaritelma
                                                                         kootut-polut
                                                                         (:gridpolku osan-polut)
                                                                         (:datapolku osan-polut)
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
                                                                                            (:gridpolku osan-polut)
                                                                                            (:datapolku osan-polut)
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
        taytetty-vaihto-osamaaritelma (reduce-kv (fn [m k v]
                                                   (assoc m k (tayta-taulukkomaaritelma v)))
                                                 {}
                                                 (:vaihto-osat maaritelma))
        vaihto-osien-mappaus (atom {:vaihto-osat {}
                                    :vaihto-osien-maaritelmat taytetty-vaihto-osamaaritelma
                                    :mappaus {}})
        #_#__ (binding [*vaihto-osien-mappaus* vaihto-osien-mappaus]
            (muodosta-vaihto-osat! vaihto-osien-mappaus taytetty-vaihto-osamaaritelma))
        osamaaritelmien-polut (muodosta-osamaaritelmien-polut taytetty-taulukkomaaritelma taytetty-vaihto-osamaaritelma (:data-polku conf))
        g (assoc (binding [*vaihto-osien-mappaus* vaihto-osien-mappaus]
                   (muodosta-grid conf taytetty-taulukkomaaritelma))
                 ::vaihto-osien-mappaus vaihto-osien-mappaus)
        datakasittely-ratom (binding [*osamaaritelmien-polut* osamaaritelmien-polut]
                              (merge (muodosta-datakasittelija-ratom taytetty-taulukkomaaritelma (:data-polku conf))
                                     (reduce-kv (fn [m _ v]
                                                  (merge m (muodosta-datakasittelija-ratom v (:data-polku conf))))
                                                {}
                                                taytetty-vaihto-osamaaritelma)))
        datakasittely-ratom-muokkaus {:aseta-arvo! (fn [tila arvo datapolku]
                                                     (assoc-in tila datapolku arvo))}
        datakasittely-ratom-trigger (binding [*vaihto-osien-mappaus* vaihto-osien-mappaus
                                              *osamaaritelmien-polut* osamaaritelmien-polut]
                                      (merge (muodosta-triggerit taytetty-taulukkomaaritelma (:data-polku conf))
                                             (reduce-kv (fn [m _ v]
                                                          (merge m (muodosta-triggerit v (:data-polku conf))))
                                                        {}
                                                        taytetty-vaihto-osamaaritelma)))
        rajapinta (merge (into {} (map (fn [k] [k any?]) (keys datakasittely-ratom)))
                         {:aseta-arvo! any?})
        rajapintakasittelija-taulukko (binding [*vaihto-osien-mappaus* vaihto-osien-mappaus
                                                *osamaaritelmien-polut* osamaaritelmien-polut]
                                        (muodosta-rajapintakasittelija-taulukko taytetty-taulukkomaaritelma))
        _ (swap! (:ratom conf) assoc-in (:grid-polku conf) g)
        g (grid/rajapinta-grid-yhdistaminen! g
                                             rajapinta
                                             (grid/datan-kasittelija (:ratom conf)
                                                                     rajapinta
                                                                     datakasittely-ratom
                                                                     datakasittely-ratom-muokkaus
                                                                     datakasittely-ratom-trigger)
                                             rajapintakasittelija-taulukko)]
    (println "datakasittely-ratom-trigger " datakasittely-ratom-trigger)
    (grid/grid-tapahtumat g
                          (:ratom conf)
                          datavaikutukset-taulukkoon)))
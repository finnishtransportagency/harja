(ns harja.domain.yllapitokohteet
  "Ylläapitokohteiden yhteisiä apureita"
  (:use [slingshot.slingshot :only [throw+]]))

(def +kohteissa-viallisia-sijainteja+ "viallisia-sijainteja")
(def +viallinen-yllapitokohteen-sijainti+ "viallinen-kohteen-sijainti")
(def +viallinen-yllapitokohdeosan-sijainti+ "viallinen-alikohteen-sijainti")
(def +viallinen-alustatoimenpiteen-sijainti+ "viallinen-alustatoimenpiteen-sijainti")

(defn tee-virhe [koodi viesti]
  {:koodi koodi :viesti viesti})

(defn validoi-kohteen-osoite [kohde-id kohteen-sijainti]
  (when (> (:aosa kohteen-sijainti) (:losa kohteen-sijainti))
    [(tee-virhe +viallinen-yllapitokohteen-sijainti+ (format "Kohteen (id: %s) alkuosa on loppuosaa isompi" kohde-id))]))

(defn alikohde-kohteen-sisalla? [kohteen-sijainti alikohteen-sijainti]
  (and (<= (:aosa kohteen-sijainti) (:aosa alikohteen-sijainti))
       (<= (:aet kohteen-sijainti) (:aet alikohteen-sijainti))
       (>= (:losa kohteen-sijainti) (:losa alikohteen-sijainti))
       (>= (:let kohteen-sijainti) (:let alikohteen-sijainti))))

(defn tarkista-alikohteet-sisaltyvat-kohteeseen [kohde-id kohteen-sijainti alikohteet]
  (mapv (fn [{:keys [tunnus sijainti]}]
          (when (not (alikohde-kohteen-sisalla? kohteen-sijainti sijainti))
            (tee-virhe +viallinen-yllapitokohdeosan-sijainti+
                       (format "Alikohde (tunnus: %s) ei ole kohteen (id: %s) sisällä." tunnus kohde-id))))
        alikohteet))

(defn tarkista-alikohteet-tayttavat-kohteen [kohde-id kohteen-sijainti alikohteet]
  (let [ensimmainen (:sijainti (first alikohteet))
        viimeinen (:sijainti (last alikohteet))]
    (when (or (not= (:aosa kohteen-sijainti) (:aosa ensimmainen))
              (not= (:aet kohteen-sijainti) (:aet ensimmainen))
              (not= (:losa kohteen-sijainti) (:losa viimeinen))
              (not= (:let kohteen-sijainti) (:let viimeinen)))
      [(tee-virhe +viallinen-yllapitokohdeosan-sijainti+
                  (format "Alikohteet eivät täytä kohdetta (id: %s)" kohde-id))])))

(defn tarkista-alikohteet-muodostavat-yhtenaisen-osuuden [alikohteet]
  (let [lisaa-virhe (fn [edellinen seuraava]
                      (conj
                        (:virheet edellinen)
                        (tee-virhe +viallinen-yllapitokohdeosan-sijainti+
                                   (format "Alikohteet (tunnus: %s ja tunnus: %s) eivät muodosta yhteistä osuutta"
                                           (:tunnus (:edellinen edellinen))
                                           (:tunnus seuraava)))))
        seuraava-jatkaa-edellista? (fn [seuraava edellinen]
                                     (and
                                       (= (get-in edellinen [:edellinen :sijainti :losa]) (:aosa (:sijainti seuraava)))
                                       (= (get-in edellinen [:edellinen :sijainti :let]) (:aet (:sijainti seuraava)))))]
    (:virheet
      (reduce
        (fn [edellinen seuraava]
          (if (seuraava-jatkaa-edellista? seuraava edellinen)
            (assoc edellinen :edellinen seuraava)
            {:edellinen seuraava
             :virheet (lisaa-virhe edellinen seuraava)}))
        {:virheet [] :edellinen (first alikohteet)}
        (rest alikohteet)))))



(defn validoi-alikohteet [kohde-id kohteen-sijainti alikohteet]
  (concat
    (tarkista-alikohteet-sisaltyvat-kohteeseen kohde-id kohteen-sijainti alikohteet)
    (tarkista-alikohteet-tayttavat-kohteen kohde-id kohteen-sijainti alikohteet)
    (tarkista-alikohteet-muodostavat-yhtenaisen-osuuden alikohteet)))

(defn tarkista-kohteen-ja-alikohteiden-sijannit
  "Tekee yksinkertaisen tarkastuksen, jolloin kohde on validi ja alikohteet ovat sen sisällä ja muodostavat yhteinäisen
  kokonaisuuden. Varsinainen validius tieverkon kannalta täytyy tarkistaa erikseen tietokantaa vasten."
  [kohde-id kohteen-sijainti alikohteet]

  (let [alikohteet (when alikohteet (sort-by (juxt #(get-in % [:sijainti :aosa]) #(get-in % [:sijainti :aet])) alikohteet))
        virheet (remove nil? (concat
                               (validoi-kohteen-osoite kohde-id kohteen-sijainti)
                               (validoi-alikohteet kohde-id kohteen-sijainti alikohteet)))]

    (when (not (empty? virheet))
      (throw+ {:type +kohteissa-viallisia-sijainteja+
               :virheet virheet}))))

(defn tarkista-alustatoimenpiteiden-sijainnit
  "Varmistaa että kaikkien alustatoimenpiteiden sijainnit ovat kohteen sijainnin sisällä"
  [kohde-id kohteen-sijainti alustatoimet]
  (let [virheet (mapv (fn [{:keys [sijainti]}]
                        (when (not (alikohde-kohteen-sisalla? kohteen-sijainti sijainti))
                          (tee-virhe +viallinen-alustatoimenpiteen-sijainti+
                                     (format "Alustatoimenpide ei ole kohteen (id: %s) sisällä." kohde-id))))
                      alustatoimet)]
    (when (not (empty? virheet))
      (throw+ {:type +kohteissa-viallisia-sijainteja+
               :virheet virheet}))))

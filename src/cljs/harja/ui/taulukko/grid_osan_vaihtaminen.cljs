(ns harja.ui.taulukko.grid-osan-vaihtaminen
  (:require [harja.ui.taulukko.impl.solu :as solu]
            [harja.ui.taulukko.protokollat.grid-osa :as gop]
            [harja.ui.taulukko.protokollat.solu :as sp]))

(defn tyhja->teksti
  ([tyhja parametrit] (tyhja->teksti tyhja parametrit {:fmt identity}))
  ([tyhja parametrit {:keys [fmt]}]
   {:pre [(instance? solu/Tyhja tyhja)
          ;; TODO parametrit spec
          ]
    :post [(instance? solu/Teksti %)]}
   (-> (solu/->Teksti (gop/id tyhja) parametrit)
       (sp/lisaa-fmt fmt)
       (merge (dissoc tyhja :id)))))

(defn tyhja->laajenna
  ([tyhja aukaise-fn auki-alussa? parametrit] (tyhja->laajenna tyhja aukaise-fn auki-alussa? parametrit {:fmt identity}))
  ([tyhja aukaise-fn auki-alussa? parametrit {:keys [fmt]}]
   {:pre [(instance? solu/Tyhja tyhja)
          ;; TODO parametrit spec
          ]
    :post [(instance? solu/Laajenna %)]}
   (-> (solu/->Laajenna (gop/id tyhja) aukaise-fn auki-alussa? parametrit)
       (sp/lisaa-fmt fmt)
       (merge (dissoc tyhja :id)))))

(defn tyhja->syote
  ([tyhja toiminnot kayttaytymiset parametrit] (tyhja->syote tyhja toiminnot kayttaytymiset parametrit {:fmt identity :fmt-aktiivinen identity}))
  ([tyhja toiminnot kayttaytymiset parametrit {:keys [fmt fmt-aktiivinen]}]
   {:pre [(instance? solu/Tyhja tyhja)
          ;; TODO parametrit spec
          ]
    :post [(instance? solu/Syote %)]}
   (-> (solu/->Syote (gop/id tyhja) toiminnot kayttaytymiset parametrit)
       (sp/lisaa-fmt fmt)
       (sp/lisaa-fmt-aktiiviselle fmt-aktiivinen)
       (merge (dissoc tyhja :id)))))

(defn teksti->tyhja
  ([teksti] (teksti->tyhja teksti nil))
  ([teksti luokat]
   {:pre [(instance? solu/Teksti teksti)
          (or (nil? luokat) (set? luokat))]
    :post [(instance? solu/Tyhja %)]}
   (solu/->Tyhja (gop/id teksti) luokat)))
#include <ctype.h>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define WIDTH 5
#define ALPHABET 25
#define BIGRAMS (ALPHABET * ALPHABET)
#define MAX_TEXT 200000
#define KEY_LEN 25
#define KEY_BUF 26
#define PERM_COUNT 120

#define TRAIN_RATIO 0.70
#define TOP_SHOW 12

typedef struct {
    int idx;
    double val;
} Ranked;

static int key_index(char ch) {
    if (ch < 'A' || ch > 'Z' || ch == 'J') {
        return -1;
    }
    if (ch > 'J') {
        return ch - 'A' - 1;
    }
    return ch - 'A';
}

static char idx_char(int idx) {
    if (idx < 0 || idx >= ALPHABET) {
        return '?';
    }
    if (idx >= 9) {
        return (char)('A' + idx + 1);
    }
    return (char)('A' + idx);
}

static int read_file_text(const char *path, char *out, int cap) {
    FILE *fp = fopen(path, "rb");
    size_t n;
    if (!fp) {
        return -1;
    }
    n = fread(out, 1, (size_t)(cap - 1), fp);
    out[n] = '\0';
    fclose(fp);
    return (int)n;
}

/* Keep letters only, uppercase, J->I. */
static int normalize_letters(const char *in, int in_len, char *out, int cap) {
    int i, w = 0;
    for (i = 0; i < in_len && w < cap - 1; ++i) {
        unsigned char c = (unsigned char)in[i];
        if (isalpha(c)) {
            char u = (char)toupper(c);
            out[w++] = (u == 'J') ? 'I' : u;
        }
    }
    out[w] = '\0';
    return w;
}

/* Build Playfair secondary plaintext with X insertion. */
static int make_secondary_plaintext(const char *src, int len, char *dst, int cap) {
    int i = 0;
    int w = 0;
    while (i < len && w < cap - 2) {
        char a = src[i++];
        if (i >= len) {
            dst[w++] = a;
            dst[w++] = 'X';
            break;
        }
        if (src[i] == a) {
            dst[w++] = a;
            dst[w++] = 'X';
        } else {
            dst[w++] = a;
            dst[w++] = src[i++];
        }
    }
    if ((w % 2) != 0 && w < cap - 1) {
        dst[w++] = 'X';
    }
    dst[w] = '\0';
    return w;
}

static void key_positions(const char key[KEY_BUF], int row[ALPHABET], int col[ALPHABET]) {
    int i;
    for (i = 0; i < ALPHABET; ++i) {
        row[i] = -1;
        col[i] = -1;
    }
    for (i = 0; i < KEY_LEN; ++i) {
        int idx = key_index(key[i]);
        row[idx] = i / WIDTH;
        col[idx] = i % WIDTH;
    }
}

static void playfair_encipher(const char key[KEY_BUF], const char *in, char *out, int len) {
    int row[ALPHABET], col[ALPHABET];
    int i;
    key_positions(key, row, col);
    for (i = 0; i < len; i += 2) {
        int a = key_index(in[i]);
        int b = key_index(in[i + 1]);
        int r1 = row[a], c1 = col[a], r2 = row[b], c2 = col[b];
        if (r1 == r2) {
            c1 = (c1 + 1) % WIDTH;
            c2 = (c2 + 1) % WIDTH;
        } else if (c1 == c2) {
            r1 = (r1 + 1) % WIDTH;
            r2 = (r2 + 1) % WIDTH;
        } else {
            int t = c1;
            c1 = c2;
            c2 = t;
        }
        out[i] = key[r1 * WIDTH + c1];
        out[i + 1] = key[r2 * WIDTH + c2];
    }
    out[len] = '\0';
}

static void playfair_decipher(const char key[KEY_BUF], const char *in, char *out, int len) {
    int row[ALPHABET], col[ALPHABET];
    int i;
    key_positions(key, row, col);
    for (i = 0; i < len; i += 2) {
        int a = key_index(in[i]);
        int b = key_index(in[i + 1]);
        int r1 = row[a], c1 = col[a], r2 = row[b], c2 = col[b];
        if (r1 == r2) {
            c1 = (c1 + WIDTH - 1) % WIDTH;
            c2 = (c2 + WIDTH - 1) % WIDTH;
        } else if (c1 == c2) {
            r1 = (r1 + WIDTH - 1) % WIDTH;
            r2 = (r2 + WIDTH - 1) % WIDTH;
        } else {
            int t = c1;
            c1 = c2;
            c2 = t;
        }
        out[i] = key[r1 * WIDTH + c1];
        out[i + 1] = key[r2 * WIDTH + c2];
    }
    out[len] = '\0';
}

static void bigram_counts(const char *text, int len, double counts[BIGRAMS]) {
    int i;
    for (i = 0; i < BIGRAMS; ++i) counts[i] = 0.0;
    for (i = 0; i + 1 < len; i += 2) {
        int a = key_index(text[i]);
        int b = key_index(text[i + 1]);
        if (a >= 0 && b >= 0) counts[a * ALPHABET + b] += 1.0;
    }
}

static void normalize_dist(double d[BIGRAMS]) {
    int i;
    double s = 0.0;
    for (i = 0; i < BIGRAMS; ++i) s += d[i];
    if (s <= 0.0) {
        return;
    }
    for (i = 0; i < BIGRAMS; ++i) d[i] /= s;
}

static int cmp_rank_desc(const void *a, const void *b) {
    const Ranked *ra = (const Ranked *)a;
    const Ranked *rb = (const Ranked *)b;
    if (ra->val < rb->val) return 1;
    if (ra->val > rb->val) return -1;
    return ra->idx - rb->idx;
}

static void rank_bigrams(const double d[BIGRAMS], Ranked out[BIGRAMS]) {
    int i;
    for (i = 0; i < BIGRAMS; ++i) {
        out[i].idx = i;
        out[i].val = d[i];
    }
    qsort(out, BIGRAMS, sizeof(Ranked), cmp_rank_desc);
}

/* Cross-entropy-like score: larger is better. */
static double score_by_bigram_model(const char *plain, int len, const double model[BIGRAMS]) {
    int i;
    double s = 0.0;
    for (i = 0; i + 1 < len; i += 2) {
        int a = key_index(plain[i]);
        int b = key_index(plain[i + 1]);
        double p;
        if (a < 0 || b < 0) continue;
        p = model[a * ALPHABET + b] + 1e-12;
        s += log(p);
    }
    return s;
}

static void key_from_keyword(const char *kw, char key[KEY_BUF]) {
    int used[26] = {0};
    int w = 0;
    int i;
    for (i = 0; kw[i] != '\0'; ++i) {
        char c = (char)toupper((unsigned char)kw[i]);
        if (c < 'A' || c > 'Z') {
            continue;
        }
        if (c == 'J') {
            c = 'I';
        }
        if (!used[c - 'A']) {
            used[c - 'A'] = 1;
            key[w++] = c;
        }
    }
    for (i = 0; i < 26; ++i) {
        char c = (char)('A' + i);
        if (c == 'J') {
            continue;
        }
        if (!used[i]) {
            key[w++] = c;
        }
    }
    key[KEY_LEN] = '\0';
}

static void key_to_square(const char key[KEY_BUF], char sq[WIDTH][WIDTH]) {
    int r, c;
    for (r = 0; r < WIDTH; ++r) {
        for (c = 0; c < WIDTH; ++c) {
            sq[r][c] = key[r * WIDTH + c];
        }
    }
}

static void square_to_key(const char sq[WIDTH][WIDTH], char key[KEY_BUF]) {
    int r, c;
    for (r = 0; r < WIDTH; ++r) {
        for (c = 0; c < WIDTH; ++c) {
            key[r * WIDTH + c] = sq[r][c];
        }
    }
    key[KEY_LEN] = '\0';
}

/* Generate key by row/col permutations + optional row/col reverse + transpose. */
static void transform_key_family(
    const char base_key[KEY_BUF],
    const int row_perm[5],
    const int col_perm[5],
    int reverse_rows,
    int reverse_cols,
    int transpose,
    char out_key[KEY_BUF]
) {
    char a[WIDTH][WIDTH], b[WIDTH][WIDTH], c[WIDTH][WIDTH];
    int r, cc;
    key_to_square(base_key, a);

    for (r = 0; r < WIDTH; ++r) {
        for (cc = 0; cc < WIDTH; ++cc) {
            int rr = reverse_rows ? (WIDTH - 1 - row_perm[r]) : row_perm[r];
            int rc = reverse_cols ? (WIDTH - 1 - col_perm[cc]) : col_perm[cc];
            b[r][cc] = a[rr][rc];
        }
    }

    if (transpose) {
        for (r = 0; r < WIDTH; ++r) {
            for (cc = 0; cc < WIDTH; ++cc) {
                c[r][cc] = b[cc][r];
            }
        }
        square_to_key(c, out_key);
    } else {
        square_to_key(b, out_key);
    }
}

static void gen_permutations_rec(int depth, int used[WIDTH], int cur[WIDTH], int out[PERM_COUNT][WIDTH], int *cnt) {
    int i;
    if (depth == WIDTH) {
        for (i = 0; i < WIDTH; ++i) {
            out[*cnt][i] = cur[i];
        }
        (*cnt)++;
        return;
    }
    for (i = 0; i < WIDTH; ++i) {
        if (!used[i]) {
            used[i] = 1;
            cur[depth] = i;
            gen_permutations_rec(depth + 1, used, cur, out, cnt);
            used[i] = 0;
        }
    }
}

static int generate_permutations_5(int out[PERM_COUNT][WIDTH]) {
    int used[WIDTH] = {0, 0, 0, 0, 0};
    int cur[WIDTH];
    int cnt = 0;
    gen_permutations_rec(0, used, cur, out, &cnt);
    return cnt;
}

static double letter_accuracy(const char *a, const char *b, int len) {
    int i, ok = 0;
    for (i = 0; i < len; ++i) {
        if (a[i] == b[i]) {
            ++ok;
        }
    }
    if (len == 0) {
        return 0.0;
    }
    return (double)ok / (double)len;
}

static void print_rank_mapping(const double plain_dist[BIGRAMS], const double cipher_dist[BIGRAMS], int topn) {
    Ranked rp[BIGRAMS], rc[BIGRAMS];
    int i;
    rank_bigrams(plain_dist, rp);
    rank_bigrams(cipher_dist, rc);
    printf("Top-%d bigram rank mapping (plain <=rank=> cipher):\n", topn);
    for (i = 0; i < topn; ++i) {
        int p = rp[i].idx;
        int c = rc[i].idx;
        printf("  %2d. %c%c <=> %c%c\n",
               i + 1,
               idx_char(p / ALPHABET), idx_char(p % ALPHABET),
               idx_char(c / ALPHABET), idx_char(c % ALPHABET));
    }
}

static void exhaustive_crack_in_family(
    const char *cipher,
    int len,
    const double plain_model[BIGRAMS],
    const char base_key[KEY_BUF],
    char best_key[KEY_BUF],
    double *best_score,
    long long *tested
) {
    int perms[PERM_COUNT][WIDTH];
    int pcount = generate_permutations_5(perms);
    int ri, ci, rr, rc, tr;
    char key[KEY_BUF], plain[MAX_TEXT];
    double best = -1e300;
    long long total = 0;

    for (ri = 0; ri < pcount; ++ri) {
        for (ci = 0; ci < pcount; ++ci) {
            for (rr = 0; rr <= 1; ++rr) {
                for (rc = 0; rc <= 1; ++rc) {
                    for (tr = 0; tr <= 1; ++tr) {
                        double s;
                        transform_key_family(base_key, perms[ri], perms[ci], rr, rc, tr, key);
                        playfair_decipher(key, cipher, plain, len);
                        s = score_by_bigram_model(plain, len, plain_model);
                        ++total;
                        if (s > best) {
                            best = s;
                            memcpy(best_key, key, KEY_BUF);
                        }
                    }
                }
            }
        }
    }

    *best_score = best;
    *tested = total;
}

static void run_scenario(
    const char *label,
    const char secret_key[KEY_BUF],
    const char base_key[KEY_BUF],
    const char *attack_secondary,
    int attack_len,
    const double plain_model[BIGRAMS]
) {
    char cipher[MAX_TEXT], recovered_plain[MAX_TEXT];
    char recovered_key[KEY_BUF];
    double cipher_dist[BIGRAMS];
    double best_score;
    long long tested;
    int use_len;
    double acc;
    int min_len = -1;
    int L;

    use_len = (attack_len / 2) * 2;

    playfair_encipher(secret_key, attack_secondary, cipher, use_len);
    bigram_counts(cipher, use_len, cipher_dist);
    normalize_dist(cipher_dist);

    printf("\n================ %s ================\n", label);
    printf("  secret key           : %s\n", secret_key);
    print_rank_mapping(plain_model, cipher_dist, TOP_SHOW);

    exhaustive_crack_in_family(cipher, use_len, plain_model, base_key, recovered_key, &best_score, &tested);
    playfair_decipher(recovered_key, cipher, recovered_plain, use_len);
    acc = letter_accuracy(recovered_plain, attack_secondary, use_len);

    printf("\n[Single Attack Demo]\n");
    printf("  ciphertext length    : %d\n", use_len);
    printf("  tested keys          : %lld\n", tested);
    printf("  recovered key        : %s\n", recovered_key);
    printf(
        "  key string equal     : %s (Playfair has equivalent keys)\n",
        strcmp(recovered_key, secret_key) == 0 ? "YES" : "NO"
    );
    printf("  plaintext accuracy   : %.2f%%\n", acc * 100.0);
    printf("  score                : %.6f\n", best_score);
    printf("  recovered head (120) : %.120s\n", recovered_plain);
    printf("  truth head (120)     : %.120s\n", attack_secondary);

    printf("\n[Ciphertext Length Sensitivity]\n");
    printf("  stopping rule: first length with plaintext accuracy >= 99.9%%.\n");
    printf("  note: exact key-string mismatch may still decrypt perfectly (equivalent Playfair keys).\n");
    for (L = 300; L <= attack_len; L += 100) {
        int n = (L / 2) * 2;
        char rk[KEY_BUF], rp[MAX_TEXT];
        double bs;
        long long tc;
        const char *key_equal;
        playfair_encipher(secret_key, attack_secondary, cipher, n);
        exhaustive_crack_in_family(cipher, n, plain_model, base_key, rk, &bs, &tc);
        playfair_decipher(rk, cipher, rp, n);
        acc = letter_accuracy(rp, attack_secondary, n);
        key_equal = (strcmp(rk, secret_key) == 0) ? "YES" : "NO";
        printf("  len=%4d => acc=%.2f%%, key_equal=%s, recovered_key=%s\n",
               n, acc * 100.0, key_equal, rk);
        if (min_len < 0 && acc >= 0.999) {
            min_len = n;
        }
    }
    if (min_len > 0) {
        printf("  => Recommended minimum secondary-plaintext length: %d\n", min_len);
    } else {
        printf("  => Not recovered exactly in tested range (up to %d).\n", attack_len);
    }

    printf("\n[Algorithm Stop Condition]\n");
    printf("  The brute-force loop stops only after all %lld candidate keys are fully enumerated.\n", tested);
}

int main(void) {
    char raw[MAX_TEXT], normalized[MAX_TEXT];
    char train_src[MAX_TEXT], attack_src[MAX_TEXT];
    char train_secondary[MAX_TEXT], attack_secondary[MAX_TEXT];
    char base_key[KEY_BUF], secret_key_case1[KEY_BUF], secret_key_case2[KEY_BUF];
    double plain_model[BIGRAMS];

    int row_perm_secret[WIDTH] = {2, 0, 4, 1, 3};
    int col_perm_secret[WIDTH] = {1, 4, 0, 2, 3};
    const char *corpus_path = "corp.txt";
    const char *base_keyword = "PLAYFAIR";
    int rev_rows_secret = 1;
    int rev_cols_secret = 0;
    int transpose_secret = 1;

    int raw_len, norm_len;
    int train_src_len, attack_src_len;
    int train_len, attack_len;

    raw_len = read_file_text(corpus_path, raw, MAX_TEXT);
    if (raw_len <= 0) {
        fprintf(stderr, "Failed to read %s\n", corpus_path);
        return 1;
    }

    norm_len = normalize_letters(raw, raw_len, normalized, MAX_TEXT);
    if (norm_len < 2500) {
        fprintf(stderr, "Corpus too short after normalization: %d\n", norm_len);
        return 1;
    }

    train_src_len = (int)(norm_len * TRAIN_RATIO);
    attack_src_len = norm_len - train_src_len;
    memcpy(train_src, normalized, (size_t)train_src_len);
    train_src[train_src_len] = '\0';
    memcpy(attack_src, normalized + train_src_len, (size_t)attack_src_len);
    attack_src[attack_src_len] = '\0';

    train_len = make_secondary_plaintext(train_src, train_src_len, train_secondary, MAX_TEXT);
    attack_len = make_secondary_plaintext(attack_src, attack_src_len, attack_secondary, MAX_TEXT);

    key_from_keyword(base_keyword, base_key);
    transform_key_family(
        base_key,
        row_perm_secret,
        col_perm_secret,
        rev_rows_secret,
        rev_cols_secret,
        transpose_secret,
        secret_key_case1
    );

    /* This key is used to demonstrate exact key-string recovery in the same run. */
    memcpy(secret_key_case2, "PNIEUAQBHWYSCKXLORGVFTDMZ", KEY_BUF);

    bigram_counts(train_secondary, train_len, plain_model);
    normalize_dist(plain_model);

    printf("[Dataset]\n");
    printf("  raw bytes            : %d\n", raw_len);
    printf("  normalized letters   : %d\n", norm_len);
    printf("  training letters     : %d -> secondary=%d\n", train_src_len, train_len);
    printf("  attack letters       : %d -> secondary=%d\n", attack_src_len, attack_len);
    printf("  base keyword key     : %s\n", base_key);
    printf("\n");

    run_scenario(
        "Case A: transformed key (equivalent-key behavior)",
        secret_key_case1,
        base_key,
        attack_secondary,
        attack_len,
        plain_model
    );

    run_scenario(
        "Case B: switched key (exact key-string recovery demo)",
        secret_key_case2,
        base_key,
        attack_secondary,
        attack_len,
        plain_model
    );

    return 0;
}

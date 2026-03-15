#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <assert.h>
#include <time.h>
#include "scoreText.h"

#define TEMP 20
#define STEP 0.2
#define COUNT 10000
#define WIDTH 5

char *playfairDecipher(char *key, char *in,char *out, int len);
float playfairCrack(char *text,int len, char* maxKey);
static char *shuffleKey(char *in);
inline int indexKeySquare(int row, int col);
inline int rowFromIndex(int index);
inline int colFromIndex(int index);

static int letterToKeyIndex(char letter){
    assert(letter >= 'A' && letter <= 'Z' && letter != 'J');
    if (letter > 'J') {
        return letter - 'A' - 1;//看成j不存在，所以j后面的字母的序号前移1位
    }
    return letter - 'A';
}

int main(int argc, char *argv[])
{
    // THINGS TO ENSURE: CIPHER AND KEY MUST BE UPPERCASE, CONSISTING ONLY OF LETTERS A-Z, AND NO OTHERS. YOU CAN SPELL OUT NUMBERS IF YOU NEED TO.
    // NEITHER THE CIPHER OR THE KEY SHOULD HAVE THE LETTER 'J' IN IT. IT WILL CRASH IF YOU DO NOT DO THESE THINGS. THIS IS A PROOF OF CONCEPT ONLY.
    char cipher[]  = "RIGAUNLPGNANYFPLHRZMUBDSLDLXTGCXGYOFQNTDGSKMKXPLHBRHAHOFLDLXEXNUPTSGFNEWHKDFSNHZOFXBHZLTPMIXLXYSOPGOLXEBNFPOFZGRSOHDPHTLLCXLHKHTOKTPTPNSMPPOBKNFSFISEOHRLDLXPSNFRMNSLTQFEIKBMPRONFARRBKCMDZTQETHFPHQGFUOLXDSELHZTLAGMKTPLEOPLTEIUGLOFMRLSDFAKIDQFNMKTPTHRLFYOFCYNSPTHKISNFVMHOGRHRFQGYKMXBSTOKZOOFHCAQOFGRYGFATGITOFMIFATHZCKBBNFMRLFLHOGLIZLEZTSFPLTPUMOFBSDSPOHZBGTPLTOKAHINZOGCGYPORDRLQZMZGDSTMDRHSDNPLXZOBDXLUHETBXIMNQKIFTMDVAFMSQMDXGANTLRLOFZOGRBKTZIMNFARDMHRINXLUHGTHRROLXPMHZBGTOKIPLXBPXDMRLZGLXHRDQDHNRIMTHNLFOUZBGXLBGITHDLEGYGEDQOPVGNFQUBOEBDMTMUNKMLEDPIOBGZKQLLEGYUYPLRHSDFASFISEOHRLDLXNFEDFLARIOLDHDBMDHLXOFCYLTLKHZBGRIGAUNLPOFNISLLEZOGCPLPOTGHMXBTDLGHZTHBXHDPTOFDPNFEGNFFRTLTIRGFXDSKIAQEKNFELTPOFHLRBSDPOTHTPIHPLAHNLZUZOBDXLUHBHIZLXAOHDSNRZZOLDHDNLNPBGBGYUPMISPLTMDHAULTELHPOFZOBDRLHRQSMPTHCGOFESLRGDRGFLDZ";
    int len = strlen(cipher);  
    char *out = malloc(sizeof(char)*(len+1));
    srand((unsigned)time(NULL)); // randomise the seed, so we get different results each time we run this program
    printf("Running playfaircrack, this could take a few minutes...\n");

    char key[] = "ABCDEFGHIKLMNOPQRSTUVWXYZ";
    int i=0;
    double score,maxscore=-99e99;
    // run until user kills it
    while(1){
        i++;
        score = playfairCrack(cipher,len,key);
        if(score > maxscore){
            maxscore = score;
            printf("best score so far: %f, on iteration %d\n",score,i);
            printf("    Key: '%s'\n",key);
            playfairDecipher(key, cipher,out, len);
            printf("    plaintext: '%s'\n",out);
        }
    }
    free(out);
    return 0;
}

void exchange2letters(char *key){
    int i = rand()%25;
    int j = rand()%25;
    char temp = key[i];
    key[i]= key[j];
    key[j] = temp;
}

void swap2rows(char *key){
    int i = rand()%5;
    int j = rand()%5;
    char temp;
    int k;
    for(k=0;k<5;k++){
        temp = key[i*5 + k];
        key[i*5 + k] = key[j*5 + k];
        key[j*5 + k] = temp;
    }
}

void swap2cols(char *key){
    int i = rand()%5;
    int j = rand()%5;
    char temp;
    int k;
    for(k=0;k<5;k++){
        temp = key[k*5 + i];
        key[k*5 + i] = key[k*5 + j];
        key[k*5 + j] = temp;
    }
}

/* key modification consists of several different modifications: swapping rows, cols, flipping the
   keysquare rows, flipping all cols and reversing the whole key. In addition to this, single letter
   swaps are made. The letter swaps occur ~90% of the time. */
void modifyKey(char *newKey,char *oldKey){
    int k,j,i = rand()%50;
    switch(i){
        case 0: strcpy(newKey,oldKey); swap2rows(newKey); break;
        case 1: strcpy(newKey,oldKey); swap2cols(newKey); break;       
        case 2: for(k=0;k<25;k++) newKey[k] = oldKey[24-k]; newKey[25] = '\0'; break; // reverse whole keysquare
        case 3: for(k=0;k<5;k++) for(j=0;j<5;j++) newKey[k*5 + j] = oldKey[(4-k)*5+j]; // swap rows up-down
                newKey[25] = '\0';
                break;
        case 4: for(k=0;k<5;k++) for(j=0;j<5;j++) newKey[j*5 + k] = oldKey[(4-j)*5+k]; // swap cols left-right
                newKey[25] = '\0';
                break;
        default:strcpy(newKey,oldKey); //就像上面的注释说的，交换两个字母（很小的变化）占比为45/50 = 90%，符合物理世界中缓慢退火的情景。
                exchange2letters(newKey);
    }
}

/* the function that implements the simulated annealing algorithm
   Input params:
     text: ciphertext
     len: length of text
     bestkey: initial key and store best key in iteration
   Output:
     return the best score in iteration
 */
float playfairCrack(char *text,int len, char* bestKey){
    char parentKey[26];
    char childKey[26];
    char bestLocalKey[26];
    char *parentPlain;
    char *childPlain;
    double parentScore;
    double childScore;
    double bestScore;
    double temperature;
    int count;

    assert(text != NULL && bestKey != NULL);

    parentPlain = malloc(sizeof(char) * (len + 1));
    childPlain = malloc(sizeof(char) * (len + 1));
    assert(parentPlain != NULL && childPlain != NULL);

    strcpy(parentKey, bestKey);
    playfairDecipher(parentKey, text, parentPlain, len);
    parentScore = scoreTextQgram(parentPlain, len);
    bestScore = parentScore;
    strcpy(bestLocalKey, parentKey);

    for (temperature = TEMP; temperature > 0.0; temperature -= STEP) {
        for (count = 0; count < COUNT; count++) {
            double deltaFitness;
            double acceptanceThreshold;
            double randomValue;

            modifyKey(childKey, parentKey);
            playfairDecipher(childKey, text, childPlain, len);
            childScore = scoreTextQgram(childPlain, len);
            deltaFitness = childScore - parentScore;

            if (deltaFitness > 0.0) {
                strcpy(parentKey, childKey);
                parentScore = childScore;
            } else {
                acceptanceThreshold = exp(deltaFitness / temperature);
                randomValue = (double) rand() / ((double) RAND_MAX + 1.0);//得到0，1之间的随机数
                if (randomValue < acceptanceThreshold) {
                    strcpy(parentKey, childKey);
                    parentScore = childScore;
                }
            }

            if (parentScore > bestScore) {
                bestScore = parentScore;
                strcpy(bestLocalKey, parentKey);
            }
        }
    }

    strcpy(bestKey, bestLocalKey);
    free(parentPlain);
    free(childPlain);
    return (float) bestScore;
}

/* the function that implements decryption algorithm
    Input params:
      key: the key used for decryption
      text: ciphertext
      result: char array to store plaintext
      len: length of text
    Output:
      return result
*/
char *playfairDecipher(char *key, char *text, char *result, int len){
    int rowPos[25];
    int colPos[25];
    int index;
    int i;

    assert(key != NULL && text != NULL && result != NULL);
    assert(len >= 0 && len % 2 == 0);

    for (i = 0; i < 25; i++) {
        rowPos[i] = -1;
        colPos[i] = -1;
    }

    for (index = 0; index < 25; index++) {
        int letterIndex = letterToKeyIndex(key[index]);//letterIndex可以看成字母本身
        rowPos[letterIndex] = rowFromIndex(index);
        colPos[letterIndex] = colFromIndex(index);
    }

    for (i = 0; i < len; i += 2) {
        int leftIndex = letterToKeyIndex(text[i]);
        int rightIndex = letterToKeyIndex(text[i + 1]);
        int leftRow;
        int leftCol;
        int rightRow;
        int rightCol;

        assert(leftIndex >= 0 && leftIndex < 25);
        assert(rightIndex >= 0 && rightIndex < 25);
        assert(rowPos[leftIndex] != -1 && colPos[leftIndex] != -1);
        assert(rowPos[rightIndex] != -1 && colPos[rightIndex] != -1);

        leftRow = rowPos[leftIndex];
        leftCol = colPos[leftIndex];
        rightRow = rowPos[rightIndex];
        rightCol = colPos[rightIndex];

        if (leftRow == rightRow) {
            leftCol = (leftCol + WIDTH - 1) % WIDTH;
            rightCol = (rightCol + WIDTH - 1) % WIDTH;
        } else if (leftCol == rightCol) {
            leftRow = (leftRow + WIDTH - 1) % WIDTH;
            rightRow = (rightRow + WIDTH - 1) % WIDTH;
        } else {
            int tempCol = leftCol;
            leftCol = rightCol;
            rightCol = tempCol;
        }

        result[i] = key[indexKeySquare(leftRow, leftCol)];
        result[i + 1] = key[indexKeySquare(rightRow, rightCol)];
    }

    result[len] = '\0';
    return result;
}

// do fisher yeates shuffle      
static char *shuffleKey(char *in){
    int i,j;
    char temp;
    for (i = 24; i >= 1; i--){
        j = rand() % (i+1);
        temp = in[j];
        in[j] = in[i];
        in[i] = temp;
    }
    return in;
} 

/**
 * 下面是一个辅助函数，就像我们把四元组的概率分布存在一维数组中，索引是通过4位26进制数到十进制数的转化实现的
 * 我们把key方阵，5X5大小，索引通过将2位5进制数到十进制数
 * 如果不是方阵，mXn大小，那么就是i*n + j*m, 类比到坐标的索引方式也是一种思考。
 */

inline int indexKeySquare(int row, int col) {
    assert(row <= 4 && row >= 0 && col <= 4 && col >= 0);//防御性编程
    return row * WIDTH + col;
}

inline int rowFromIndex(int index) {
    assert(index <= 24 && index >= 0);
    return index / WIDTH;
}

inline int colFromIndex(int index) {
    assert(index <= 24 && index >= 0);
    return index % WIDTH;
}